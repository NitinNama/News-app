import urllib.parse
import difflib
import logging
from datetime import datetime, timedelta
from typing import List, Set, Dict, Tuple, Optional

from .models import FeedEntry

logger = logging.getLogger("rss_parser.deduplicator")

def normalize_url(url: str) -> str:
    """
    Cleans and normalizes URLs to avoid duplicate entries under tracking/referral variants.
    Strips UTM parameters, fragments, and standard tracking query variables.
    """
    if not url:
        return ""
    try:
        # Strip whitespaces and parse URL
        url = url.strip()
        parsed = urllib.parse.urlparse(url)
        
        # Deconstruct query parameters
        queries = urllib.parse.parse_qsl(parsed.query)
        
        # Filter out common referral/analytic query parameters
        blacklisted_params = {
            'utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 
            'utm_content', 'ref', 'rss', 'feed', 'newsletter', 'origin', 'source'
        }
        filtered_queries = [(k, v) for k, v in queries if k.lower() not in blacklisted_params]
        
        # Reconstruct query string
        clean_query = urllib.parse.urlencode(filtered_queries)
        
        # Reconstruct full URL (omitting fragment/anchors for canonical representation)
        normalized = urllib.parse.urlunparse((
            parsed.scheme.lower(),
            parsed.netloc.lower(),
            parsed.path,
            parsed.params,
            clean_query,
            "" # Empty fragment
        ))
        return normalized
    except Exception as e:
        logger.warning(f"Failed to normalize URL {url}: {e}")
        return url.strip().lower()

class FeedDeduplicator:
    """
    Handles robust de-duplication of FeedEntry items using exact matching
    on normalized URLs, GUIDs, and fuzzy title matching over configurable time windows.
    """

    def __init__(
        self, 
        similarity_threshold: float = 0.85, 
        time_window_days: int = 7
    ):
        self.similarity_threshold = similarity_threshold
        self.time_window = timedelta(days=time_window_days)
        
        # Memory storage states
        self.seen_guids: Set[str] = set()
        self.seen_urls: Set[str] = set()
        
        # Store tuples of (normalized_title, published_datetime, entry) for title-similarity checking
        self.history: List[Tuple[str, datetime, FeedEntry]] = []

    def clean_old_records(self, reference_time: Optional[datetime] = None) -> None:
        """Prunes historical tracking entries that fall outside the configured time window."""
        ref = reference_time or datetime.now()
        cutoff = ref - self.time_window
        
        # Filter history
        active_history = []
        active_guids = set()
        active_urls = set()
        
        for norm_title, pub_time, entry in self.history:
            if pub_time >= cutoff:
                active_history.append((norm_title, pub_time, entry))
                active_guids.add(entry.guid)
                active_urls.add(normalize_url(entry.link))
                
        self.history = active_history
        self.seen_guids = active_guids
        self.seen_urls = active_urls
        logger.debug(f"Pruned historical data. Active records: {len(self.history)}")

    def is_duplicate(self, entry: FeedEntry) -> Tuple[bool, str]:
        """
        Determines whether the given FeedEntry is a duplicate.
        Returns a tuple of (is_duplicate, reason_string).
        """
        # 1. Check direct GUID match
        if entry.guid in self.seen_guids:
            return True, f"Exact GUID match: {entry.guid}"
            
        # 2. Check normalized URL match
        norm_url = normalize_url(entry.link)
        if norm_url in self.seen_urls:
            return True, f"Normalized URL match: {norm_url}"
            
        # 3. Check fuzzy title similarity within time window
        # Normalize title: strip punctuation, lowercase, strip whitespaces
        norm_title = "".join(ch for ch in entry.title.lower() if ch.isalnum() or ch.isspace()).strip()
        
        entry_pub = entry.published or datetime.now()
        
        # Scan through history
        for hist_title, hist_pub, hist_entry in self.history:
            # Only perform title similarity check if they published within the active time window
            if abs(entry_pub - hist_pub) <= self.time_window:
                # Fast pre-filter: check if major substring matches or lengths are highly divergent
                len_diff = abs(len(norm_title) - len(hist_title))
                max_len = max(len(norm_title), len(hist_title))
                
                if max_len > 0 and (len_diff / max_len) > (1.0 - self.similarity_threshold):
                    # Length difference is too high to meet threshold
                    continue
                
                # Check Levenshtein ratio approximation via SequenceMatcher
                matcher = difflib.SequenceMatcher(None, norm_title, hist_title)
                ratio = matcher.quick_ratio()
                
                if ratio >= self.similarity_threshold:
                    # Upgrade to full ratio check for absolute precision
                    precision_ratio = matcher.ratio()
                    if precision_ratio >= self.similarity_threshold:
                        return True, f"Fuzzy title match ({precision_ratio:.2f} score) with '{hist_entry.title}'"
                        
        return False, ""

    def process(self, entries: List[FeedEntry], prune: bool = True) -> List[FeedEntry]:
        """
        Filters out duplicates from a list of feed entries.
        Adds new entries to the historical index.
        """
        if prune:
            self.clean_old_records()
            
        unique_entries = []
        for entry in entries:
            is_dup, reason = self.is_duplicate(entry)
            if is_dup:
                logger.info(f"Filtered out duplicate article: '{entry.title}' [{reason}]")
                continue
                
            # Add to state tracking indexes
            self.seen_guids.add(entry.guid)
            
            norm_url = normalize_url(entry.link)
            self.seen_urls.add(norm_url)
            
            norm_title = "".join(ch for ch in entry.title.lower() if ch.isalnum() or ch.isspace()).strip()
            pub_time = entry.published or datetime.now()
            
            self.history.append((norm_title, pub_time, entry))
            unique_entries.append(entry)
            
        return unique_entries

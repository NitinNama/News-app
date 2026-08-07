import urllib.request
import urllib.parse
import xml.etree.ElementTree as ET
import logging
import re
from datetime import datetime
from html.parser import HTMLParser
from typing import List, Optional, Tuple
from email.utils import parsedate_to_datetime

from .models import FeedSource, FeedEntry

# Configure logger
logger = logging.getLogger("rss_parser.parser")

class HTMLStripper(HTMLParser):
    """Simple HTML parser to strip tags and decode HTML entities."""
    def __init__(self):
        super().__init__()
        self.reset()
        self.strict = False
        self.convert_charrefs = True
        self.text = []

    def handle_data(self, d):
        self.text.append(d)

    def get_data(self):
        return "".join(self.text).strip()

def strip_html(html_content: str) -> str:
    """Safely strips HTML tags and cleans up extra whitespaces."""
    if not html_content:
        return ""
    try:
        stripper = HTMLStripper()
        stripper.feed(html_content)
        cleaned = stripper.get_data()
        # Collapse multiple spaces or newlines
        return re.sub(r'\s+', ' ', cleaned)
    except Exception as e:
        logger.warning(f"Failed to strip HTML content: {e}. Using regex fallback.")
        # Fallback regex stripping
        cleaned = re.sub(r'<[^>]*>', '', html_content)
        return re.sub(r'\s+', ' ', cleaned).strip()

def parse_iso_datetime(date_str: str) -> Optional[datetime]:
    """Tries parsing standard ISO/Atom timestamp formats."""
    date_str = date_str.strip()
    for fmt in (
        "%Y-%m-%dT%H:%M:%S%z",
        "%Y-%m-%dT%H:%M:%S.%f%z",
        "%Y-%m-%dT%H:%M:%SZ",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d"
    ):
        try:
            if fmt.endswith("Z") and date_str.endswith("Z"):
                # Replace Z with UTC offset for parsing
                return datetime.strptime(date_str.replace("Z", "+0000"), "%Y-%m-%dT%H:%M:%S%z")
            return datetime.strptime(date_str, fmt)
        except ValueError:
            continue
    return None

def parse_rfc822_datetime(date_str: str) -> Optional[datetime]:
    """Tries parsing RFC 822 / RSS 2.0 pubDate timestamp formats."""
    try:
        return parsedate_to_datetime(date_str.strip())
    except Exception:
        return None

def parse_pub_date(date_str: Optional[str]) -> Optional[datetime]:
    """Attempts multiple common RSS and Atom date parsing strategies."""
    if not date_str:
        return None
    
    # Try RFC 822 (standard for RSS 2.0)
    dt = parse_rfc822_datetime(date_str)
    if dt:
        return dt
        
    # Try ISO formats (standard for Atom)
    dt = parse_iso_datetime(date_str)
    if dt:
        return dt
        
    # Last resort fallback if date string contains known layout
    try:
        # Strip timezone string names (like EST, GMT) if they cause issues
        clean_str = re.sub(r'\s+[A-Z]{3,4}$', '', date_str.strip())
        return parsedate_to_datetime(clean_str)
    except Exception:
        pass
        
    return None

class FeedParser:
    """Handles fetching, parsing, and normalizing RSS & Atom XML feeds."""

    def __init__(self, default_headers: Optional[dict] = None):
        self.default_headers = default_headers or {
            "User-Agent": "PulseRSSParser/1.0.0 (+https://github.com/example/pulse-news)"
        }

    def fetch_feed_xml(self, source: FeedSource) -> Tuple[Optional[str], Optional[str]]:
        """
        Fetches the XML content from the source URL.
        Returns a tuple of (xml_content, final_url) or (None, None) on failure.
        """
        headers = {**self.default_headers, **source.headers}
        req = urllib.request.Request(source.url, headers=headers)
        
        try:
            logger.info(f"Fetching feed from: {source.url}")
            with urllib.request.urlopen(req, timeout=source.timeout) as response:
                content_bytes = response.read()
                
                # Detect charset encoding
                charset = "utf-8"
                content_type = response.headers.get("Content-Type", "")
                if "charset=" in content_type:
                    charset = content_type.split("charset=")[-1].strip()
                
                try:
                    xml_content = content_bytes.decode(charset, errors="replace")
                except Exception:
                    xml_content = content_bytes.decode("utf-8", errors="replace")
                    
                return xml_content, response.geturl()
        except Exception as e:
            logger.error(f"Failed to fetch feed from {source.url}: {e}")
            return None, None

    def parse_and_normalize(self, source: FeedSource) -> List[FeedEntry]:
        """
        Fetches the feed and processes it into normalized FeedEntry items.
        Handles both RSS (2.0/1.0) and Atom formats.
        """
        xml_content, final_url = self.fetch_feed_xml(source)
        if not xml_content:
            return []

        # Optional: Integrate feedparser library if available as a higher-fidelity parsing layer
        try:
            import feedparser
            logger.debug("Optional dependency 'feedparser' found. Using it for high-fidelity parsing.")
            return self._parse_with_feedparser(xml_content, source, final_url)
        except ImportError:
            logger.debug("Using built-in xml.etree.ElementTree XML parser fallback.")
            return self._parse_with_element_tree(xml_content, source, final_url)

    def _parse_with_feedparser(self, xml_content: str, source: FeedSource, final_url: Optional[str]) -> List[FeedEntry]:
        """Parses feed content using the robust third-party feedparser package."""
        import feedparser
        parsed = feedparser.parse(xml_content)
        entries = []
        
        source_title = source.name or parsed.feed.get("title", "Unknown Source")
        
        for entry in parsed.entries:
            title = entry.get("title", "No Title")
            
            # Find the primary link
            link = entry.get("link", "")
            if not link and getattr(entry, "links", None):
                for l in entry.links:
                    if l.get("rel") == "alternate" or not l.get("rel"):
                        link = l.get("href", "")
                        break
            if not link:
                link = final_url or source.url

            guid = entry.get("id", link or title)
            
            # Extract and normalize summary
            summary_content = ""
            if "summary" in entry:
                summary_content = entry.summary
            elif "content" in entry and isinstance(entry.content, list) and len(entry.content) > 0:
                summary_content = entry.content[0].value
            elif "description" in entry:
                summary_content = entry.description

            summary = strip_html(summary_content)
            
            # Parse published date
            published_dt = None
            if "published_parsed" in entry and entry.published_parsed:
                try:
                    published_dt = datetime(*entry.published_parsed[:6])
                except Exception:
                    pass
            elif "updated_parsed" in entry and entry.updated_parsed:
                try:
                    published_dt = datetime(*entry.updated_parsed[:6])
                except Exception:
                    pass
            
            if not published_dt:
                published_str = entry.get("published", entry.get("updated", entry.get("pubDate")))
                published_dt = parse_pub_date(published_str)

            author = entry.get("author", entry.get("author_detail", {}).get("name"))
            
            entries.append(FeedEntry(
                title=title.strip(),
                link=link.strip(),
                guid=str(guid).strip(),
                summary=summary,
                published=published_dt,
                author=author,
                source_name=source_title,
                raw_data=dict(entry)
            ))
            
        return entries

    def _parse_with_element_tree(self, xml_content: str, source: FeedSource, final_url: Optional[str]) -> List[FeedEntry]:
        """Parses feed content using standard library XML parser."""
        try:
            # Strip potential leading/trailing whitespaces that break parser
            xml_content = xml_content.strip()
            root = ET.fromstring(xml_content)
        except Exception as e:
            logger.error(f"XML Parsing Exception for source {source.url}: {e}")
            return []

        entries = []
        
        # Check if Root is RSS or Atom
        tag_lower = root.tag.lower()
        if "rss" in tag_lower or tag_lower == "rdf" or root.find(".//channel") is not None:
            # Parse RSS 1.0 or 2.0
            channel = root.find(".//channel")
            source_title = source.name
            if not source_title and channel is not None:
                title_elem = channel.find("title")
                if title_elem is not None:
                    source_title = title_elem.text
            if not source_title:
                source_title = "Unknown RSS Source"

            items = root.findall(".//item")
            for item in items:
                title_elem = item.find("title")
                title = title_elem.text if title_elem is not None else "No Title"

                link_elem = item.find("link")
                link = link_elem.text if link_elem is not None else ""
                if not link:
                    link = final_url or source.url

                guid_elem = item.find("guid")
                guid = guid_elem.text if guid_elem is not None else (link or title)

                desc_elem = item.find("description")
                summary_raw = desc_elem.text if desc_elem is not None else ""
                summary = strip_html(summary_raw)

                pub_elem = item.find("pubDate")
                pub_str = pub_elem.text if pub_elem is not None else None
                published_dt = parse_pub_date(pub_str)

                creator_elem = item.find("{http://purl.org/dc/elements/1.1/}creator")
                author = creator_elem.text if creator_elem is not None else None
                if not author:
                    author_elem = item.find("author")
                    author = author_elem.text if author_elem is not None else None

                raw_data = {
                    "title": title,
                    "link": link,
                    "guid": guid,
                    "description": summary_raw,
                    "pubDate": pub_str,
                    "author": author
                }

                entries.append(FeedEntry(
                    title=title.strip(),
                    link=link.strip(),
                    guid=str(guid).strip(),
                    summary=summary,
                    published=published_dt,
                    author=author,
                    source_name=source_title,
                    raw_data=raw_data
                ))

        elif "feed" in tag_lower:
            # Parse Atom
            ns = {"atom": "http://www.w3.org/2005/Atom"}
            
            source_title = source.name
            if not source_title:
                title_elem = root.find("atom:title", ns)
                if title_elem is not None:
                    source_title = title_elem.text
            if not source_title:
                source_title = "Unknown Atom Source"

            items = root.findall("atom:entry", ns)
            # If default namespace isn't registering correctly, search universally
            if not items:
                items = root.findall(".//{http://www.w3.org/2005/Atom}entry")
            if not items:
                # Direct lookup if no XML namespaces are configured properly
                items = root.findall(".//entry")

            for item in items:
                # Helper search with namespace support
                def find_child_text(tag: str) -> Optional[str]:
                    for prefix in ("", "{http://www.w3.org/2005/Atom}"):
                        el = item.find(f"{prefix}{tag}")
                        if el is not None:
                            return el.text
                    return None

                title = find_child_text("title") or "No Title"
                
                # Extract Atom alternate link
                link = ""
                for prefix in ("", "{http://www.w3.org/2005/Atom}"):
                    links = item.findall(f"{prefix}link")
                    for l in links:
                        rel = l.attrib.get("rel", "alternate")
                        if rel == "alternate" or not rel:
                            link = l.attrib.get("href", "")
                            break
                    if link:
                        break
                if not link:
                    link = final_url or source.url

                guid = find_child_text("id") or link or title

                # Summary or content fallback
                summary_raw = find_child_text("summary") or find_child_text("content") or ""
                summary = strip_html(summary_raw)

                pub_str = find_child_text("published") or find_child_text("updated")
                published_dt = parse_pub_date(pub_str)

                # Author search
                author = None
                for prefix in ("", "{http://www.w3.org/2005/Atom}"):
                    author_el = item.find(f"{prefix}author")
                    if author_el is not None:
                        name_el = author_el.find(f"{prefix}name") or author_el.find("name")
                        if name_el is not None:
                            author = name_el.text
                            break

                raw_data = {
                    "title": title,
                    "link": link,
                    "guid": guid,
                    "summary": summary_raw,
                    "published": pub_str,
                    "author": author
                }

                entries.append(FeedEntry(
                    title=title.strip(),
                    link=link.strip(),
                    guid=str(guid).strip(),
                    summary=summary,
                    published=published_dt,
                    author=author,
                    source_name=source_title,
                    raw_data=raw_data
                ))
        else:
            logger.warning(f"Unsupported feed root tag structure: {root.tag}")

        return entries

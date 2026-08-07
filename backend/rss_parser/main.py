import argparse
import json
import logging
import sys
from datetime import datetime
from typing import List

from .models import FeedSource, FeedEntry
from .parser import FeedParser
from .deduplicator import FeedDeduplicator

def setup_logging(verbose: bool):
    """Configures modern logging format for CLI feedback."""
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
        handlers=[
            logging.StreamHandler(sys.stdout)
        ]
    )

def run_pipeline(sources: List[FeedSource], similarity_threshold: float, time_window_days: int) -> List[FeedEntry]:
    """Runs the full fetching, parsing, normalization, and deduplication pipeline."""
    parser = FeedParser()
    deduplicator = FeedDeduplicator(
        similarity_threshold=similarity_threshold,
        time_window_days=time_window_days
    )
    
    all_entries: List[FeedEntry] = []
    
    for source in sources:
        try:
            entries = parser.parse_and_normalize(source)
            print(f"Parsed {len(entries)} items from {source.name or source.url}")
            all_entries.extend(entries)
        except Exception as e:
            print(f"Error processing {source.url}: {e}", file=sys.stderr)
            
    print(f"\nTotal fetched entries before deduplication: {len(all_entries)}")
    
    # Run the deduplicator
    unique_entries = deduplicator.process(all_entries)
    
    print(f"Total unique entries after deduplication: {len(unique_entries)}")
    print(f"Removed {len(all_entries) - len(unique_entries)} duplicate items.\n")
    
    return unique_entries

def main():
    parser = argparse.ArgumentParser(
        description="Pulse Backend RSS Parser Module: Handles fetching, normalizing, and de-duplicating XML Feeds."
    )
    parser.add_argument(
        "--urls", nargs="+", help="Space-separated list of RSS/Atom feed URLs to parse."
    )
    parser.add_argument(
        "--config", type=str, help="Path to a JSON file containing feed source configurations."
    )
    parser.add_argument(
        "--threshold", type=float, default=0.85,
        help="Cosine/Levenshtein similarity threshold for fuzzy title deduplication (default: 0.85)."
    )
    parser.add_argument(
        "--window", type=int, default=7,
        help="Time window in days to look back for duplicates (default: 7)."
    )
    parser.add_argument(
        "--output", type=str, help="Path to save the normalized, deduplicated JSON output."
    )
    parser.add_argument(
        "--verbose", action="store_true", help="Enable verbose debug logs."
    )
    parser.add_argument(
        "--demo", action="store_true", help="Run the parser pipeline on a set of live tech/finance feeds."
    )

    args = parser.parse_args()
    setup_logging(args.verbose)

    feed_sources: List[FeedSource] = []

    if args.demo:
        print("--- Running Pulse RSS Parser Demo ---")
        # Define some high-quality, stable public feeds for the demo
        feed_sources = [
            FeedSource(url="https://news.ycombinator.com/rss", name="Hacker News"),
            FeedSource(url="https://feeds.feedburner.com/TechCrunch/", name="TechCrunch"),
            FeedSource(url="https://www.coindesk.com/arc/outboundfeeds/rss/", name="CoinDesk")
        ]
    elif args.config:
        try:
            with open(args.config, "r") as f:
                config_data = json.load(f)
                for item in config_data:
                    feed_sources.append(FeedSource(
                        url=item["url"],
                        name=item.get("name"),
                        category=item.get("category"),
                        headers=item.get("headers", {})
                    ))
        except Exception as e:
            print(f"Error loading config file {args.config}: {e}", file=sys.stderr)
            sys.exit(1)
    elif args.urls:
        for i, url in enumerate(args.urls):
            feed_sources.append(FeedSource(url=url, name=f"Source {i+1}"))
    else:
        # If no arguments provided, show help and run a micro-mock test demo to showcase functionality
        print("No input feeds specified. Showing interactive usage and running micro-mock simulation...\n")
        parser.print_help()
        print("\n--- Running Micro-Mock Offline Simulation ---")
        
        # Test simulation
        sim_sources = [FeedSource(url="http://mock.feed/rss", name="Mock Channel")]
        # Create mock entries for testing the deduplicator directly
        mock_entries = [
            FeedEntry(
                title="Gemini 1.5 Pro Unleashed with Ultra-Long Context",
                link="https://ai.google/blog/gemini-1-5-pro?utm_source=rss",
                guid="guid_1",
                summary="Google releases Gemini 1.5 Pro with an expansive context window.",
                published=datetime.now()
            ),
            # Duplicate 1: Exact GUID Match
            FeedEntry(
                title="Gemini 1.5 Pro Unleashed with Ultra-Long Context",
                link="https://ai.google/blog/gemini-1-5-pro?utm_source=rss",
                guid="guid_1",
                summary="Google releases Gemini 1.5 Pro with an expansive context window.",
                published=datetime.now()
            ),
            # Duplicate 2: Normalized URL Match (different UTM params, same URL)
            FeedEntry(
                title="Gemini 1.5 Pro Unleashed with Ultra-Long Context",
                link="https://ai.google/blog/gemini-1-5-pro?utm_source=newsletter&ref=rss",
                guid="guid_2_new_guid",
                summary="Google releases Gemini 1.5 Pro with an expansive context window.",
                published=datetime.now()
            ),
            # Duplicate 3: Fuzzy title match (different title wording, same context)
            FeedEntry(
                title="Gemini 1.5 Pro Unleashed with Ultra Long Context!",
                link="https://ai.google/blog/gemini-1-5-pro-alt",
                guid="guid_3",
                summary="Google announces Gemini 1.5 Pro context updates.",
                published=datetime.now()
            ),
            # Unique Entry
            FeedEntry(
                title="Pulse Android App Achieves Flawless Build in AI Studio",
                link="https://ai.studio/build/news-pulse",
                guid="guid_4",
                summary="Pulse releases customizable launcher icons for enhanced user experience.",
                published=datetime.now()
            )
        ]
        
        dedup = FeedDeduplicator(similarity_threshold=0.85)
        uniques = dedup.process(mock_entries)
        
        print("\nMock Feed Deduplication Results:")
        print(f"Input count: {len(mock_entries)}")
        print(f"Output count: {len(uniques)}")
        print("\nUnique entries extracted:")
        for idx, item in enumerate(uniques):
            print(f" {idx+1}. {item.title} (URL: {item.link})")
        print("\nTo fetch live feeds, run: python -m backend.rss_parser.main --demo\n")
        return

    # Run pipeline for demo or user feeds
    results = run_pipeline(feed_sources, args.threshold, args.window)
    serialized = [entry.to_dict() for entry in results]

    if args.output:
        try:
            with open(args.output, "w") as f:
                json.dump(serialized, f, indent=2)
            print(f"Saved {len(serialized)} normalized entries to {args.output}")
        except Exception as e:
            print(f"Error writing to output file {args.output}: {e}", file=sys.stderr)
    else:
        # Print top 3 sample results if output file not specified
        print("Sample of normalized & deduplicated entries (Top 3):")
        for i, item in enumerate(serialized[:3]):
            print(f"\n[{i+1}] Title: {item['title']}")
            print(f"    Source: {item['source_name']}")
            print(f"    Link: {item['link']}")
            print(f"    Published: {item.get('published')}")
            print(f"    Summary: {item['summary'][:150]}...")
        if len(serialized) > 3:
            print(f"\n... and {len(serialized) - 3} more entries.")

if __name__ == "__main__":
    main()

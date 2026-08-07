# Pulse RSS & Atom Feed Parser (Backend Module)

A modular, robust, and high-performance **Python backend module** designed for parsing, normalizing, and de-duplicating RSS and Atom feeds from multiple global news sources.

---

## 🌟 Key Features

1. **Diverse Format Support**:
   - Parses RSS 2.0, RSS 1.0, RDF, and Atom feed XML standards using standard-library `xml.etree.ElementTree`.
   - Automatically detects and falls back to use the popular third-party `feedparser` package if present in the environment for advanced parser compliance.
2. **Robust Content Normalization**:
   - Compiles diverse naming conventions (e.g., `<pubDate>`, `<updated>`, `<published>`) into a unified, UTC-aware Python `datetime`.
   - Standardizes descriptions/summaries and strips redundant HTML markup, scripts, and excessive whitespace safely.
   - Outputs highly clean, structure-aligned Python `FeedEntry` dataclasses.
3. **Multi-Tiered De-duplication Engine**:
   - **Exact GUID Checking**: Eliminates direct primary identifier matches.
   - **URL Normalization**: Strips UTM trackers (`utm_source`, `utm_medium`, `utm_campaign`, etc.), URL fragments, and tracking parameters before comparison to resolve alternative referrers.
   - **Fuzzy Title Matching**: Computes a Levenshtein-based similarity ratio between article titles published within a configurable time window (e.g. 7 days) to filter rewrites of the same story across syndicates.
   - **Bounded Memory Profile**: Automatically prunes expired tracking states beyond the lookback window to maintain constant memory efficiency.

---

## 🛠️ Module Architecture

The package is structured cleanly for both CLI consumption and easy integration as an imported service:

```
backend/
├── rss_parser/
│   ├── __init__.py          # Exported classes and functions
│   ├── models.py            # Typed dataclasses (FeedSource, FeedEntry)
│   ├── parser.py            # XML fetcher and format standardizer
│   ├── deduplicator.py      # Normalization and Levenshtein similarity engine
│   └── main.py              # CLI controller and pipeline coordinator
├── requirements.txt         # Optional enhancements (feedparser)
└── README.md                # System configuration and documentation
```

---

## 🚀 Usage Guide

### Prerequisites
The parser works entirely with **zero external dependencies** out of the box using built-in standard Python libraries (`xml.etree.ElementTree`, `urllib.request`, `difflib`). 

To activate advanced capabilities (higher fidelity parsing), install the dependencies:
```bash
pip install -r backend/requirements.txt
```

### Running the Interactive Simulation
You can run a built-in offline simulation that demonstrates the URL normalizer and fuzzy-deduplicator handling diverse duplicate patterns:
```bash
python -m backend.rss_parser.main
```

### Running the Live Demo
Process real-time live feeds (e.g., Hacker News, TechCrunch, CoinDesk), normalize their structure, and de-duplicate overlapping items:
```bash
python -m backend.rss_parser.main --demo
```

### Processing Custom Feeds via CLI
You can parse a space-separated list of live XML links directly from the terminal:
```bash
python -m backend.rss_parser.main --urls https://news.ycombinator.com/rss https://feeds.feedburner.com/TechCrunch/
```

### Parsing Custom Feeds with Output JSON Saved
Save the normalized, de-duplicated outputs into a structured JSON database:
```bash
python -m backend.rss_parser.main --demo --output normalized_feeds.json
```

### Advanced Configurations
Configure similarity sensitivity and historical lookback windows:
```bash
python -m backend.rss_parser.main \
  --demo \
  --threshold 0.90 \
  --window 14 \
  --output custom_pulse_feeds.json \
  --verbose
```

---

## 💻 Importable API Usage Example

Integrate this parser into any wider Python script, scheduler, or backend microservice:

```python
from datetime import datetime
from backend.rss_parser import FeedSource, FeedParser, FeedDeduplicator

# 1. Setup Sources
sources = [
    FeedSource(url="https://news.ycombinator.com/rss", name="Hacker News"),
    FeedSource(url="https://feeds.feedburner.com/TechCrunch/", name="TechCrunch")
]

# 2. Fetch and Parse
parser = FeedParser()
all_entries = []
for src in sources:
    entries = parser.parse_and_normalize(src)
    all_entries.extend(entries)

# 3. Deduplicate
deduplicator = FeedDeduplicator(similarity_threshold=0.85, time_window_days=7)
unique_entries = deduplicator.process(all_entries)

# 4. Print Unique Feed Results
for entry in unique_entries:
    print(f"[{entry.source_name}] {entry.title} - {entry.link}")
```

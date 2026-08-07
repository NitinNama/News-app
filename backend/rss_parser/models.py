from dataclasses import dataclass, field, asdict
from datetime import datetime
from typing import Optional, List, Dict, Any

@dataclass
class FeedSource:
    url: str
    name: Optional[str] = None
    category: Optional[str] = None
    headers: Dict[str, str] = field(default_factory=dict)
    timeout: int = 10

@dataclass
class FeedEntry:
    title: str
    link: str
    guid: str
    summary: str
    published: Optional[datetime] = None
    author: Optional[str] = None
    source_name: Optional[str] = None
    raw_data: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        """Converts the feed entry into a JSON-serializable dictionary."""
        data = asdict(self)
        if self.published:
            data['published'] = self.published.isoformat()
        return data

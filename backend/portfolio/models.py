from dataclasses import dataclass, field, asdict
from datetime import datetime
from enum import Enum
from typing import Optional, Dict, Any, List

class AssetType(str, Enum):
    STOCK = "STOCK"
    CURRENCY = "CURRENCY"

@dataclass
class Asset:
    symbol: str
    name: str
    asset_type: AssetType
    base_price: float
    precision: int = 2

@dataclass
class PortfolioItem:
    symbol: str
    name: str
    asset_type: AssetType
    purchase_price: float
    quantity: float
    added_at: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> Dict[str, Any]:
        data = asdict(self)
        data['asset_type'] = self.asset_type.value
        data['added_at'] = self.added_at.isoformat()
        return data

@dataclass
class MarketQuote:
    symbol: str
    price: float
    change: float
    change_percent: float
    bid: float
    ask: float
    volume: int
    high: float
    low: float
    last_updated: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> Dict[str, Any]:
        data = asdict(self)
        data['last_updated'] = self.last_updated.isoformat()
        return data

@dataclass
class ItemValuation:
    item: PortfolioItem
    current_price: float
    current_value: float
    total_cost: float
    gain_loss: float
    gain_loss_percent: float

    def to_dict(self) -> Dict[str, Any]:
        return {
            "item": self.item.to_dict(),
            "current_price": self.current_price,
            "current_value": self.current_value,
            "total_cost": self.total_cost,
            "gain_loss": self.gain_loss,
            "gain_loss_percent": self.gain_loss_percent
        }

@dataclass
class PortfolioSummary:
    total_value: float
    total_cost: float
    overall_gain: float
    overall_gain_percent: float
    valuations: List[ItemValuation] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "total_value": self.total_value,
            "total_cost": self.total_cost,
            "overall_gain": self.overall_gain,
            "overall_gain_percent": self.overall_gain_percent,
            "valuations": [val.to_dict() for val in self.valuations]
        }

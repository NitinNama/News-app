import os
import json
import logging
from typing import List, Dict, Any, Optional

from .models import PortfolioItem, PortfolioSummary, ItemValuation, AssetType
from .market_data import MockFinancialDataService

logger = logging.getLogger("portfolio.manager")

DEFAULT_STORAGE_PATH = "portfolio.json"

class PortfolioManager:
    """
    Manages client-side or backend-persisted holdings, 
    allowing seamless add, delete, and real-time refresh calculations.
    """
    def __init__(self, storage_path: str = DEFAULT_STORAGE_PATH, market_service: Optional[MockFinancialDataService] = None):
        self.storage_path = storage_path
        self.market_service = market_service or MockFinancialDataService()
        self.items: Dict[str, PortfolioItem] = {}
        self.load_portfolio()

    def load_portfolio(self) -> None:
        """Loads saved portfolio items from the JSON database file."""
        if not os.path.exists(self.storage_path):
            self.items = {}
            logger.info("No saved portfolio file found. Initializing empty portfolio.")
            return

        try:
            with open(self.storage_path, "r") as f:
                data = json.load(f)
                self.items = {}
                for key, val in data.items():
                    item = PortfolioItem(
                        symbol=val["symbol"],
                        name=val["name"],
                        asset_type=AssetType(val["asset_type"]),
                        purchase_price=val["purchase_price"],
                        quantity=val["quantity"]
                    )
                    self.items[item.symbol.upper()] = item
            logger.info(f"Loaded {len(self.items)} items from {self.storage_path}")
        except Exception as e:
            logger.error(f"Failed to load portfolio from {self.storage_path}: {e}")
            self.items = {}

    def save_portfolio(self) -> bool:
        """Saves current portfolio holdings into the local JSON file."""
        try:
            serializable = {symbol: item.to_dict() for symbol, item in self.items.items()}
            with open(self.storage_path, "w") as f:
                json.dump(serializable, f, indent=2)
            logger.debug(f"Saved portfolio to {self.storage_path}")
            return True
        except Exception as e:
            logger.error(f"Failed to save portfolio: {e}")
            return False

    def add_holding(self, symbol: str, name: str, asset_type: str, purchase_price: float, quantity: float) -> PortfolioItem:
        """
        Adds a new stock or currency pair holding.
        If the holding already exists, it calculates the new weighted-average purchase price.
        """
        symbol_upper = symbol.strip().upper()
        type_enum = AssetType(asset_type.strip().upper())
        
        if symbol_upper in self.items:
            # Average cost calculation
            existing = self.items[symbol_upper]
            new_qty = existing.quantity + quantity
            if new_qty > 0:
                weighted_price = ((existing.purchase_price * existing.quantity) + (purchase_price * quantity)) / new_qty
            else:
                weighted_price = purchase_price
            
            item = PortfolioItem(
                symbol=symbol_upper,
                name=name,
                asset_type=type_enum,
                purchase_price=round(weighted_price, 4 if type_enum == AssetType.CURRENCY else 2),
                quantity=new_qty
            )
        else:
            item = PortfolioItem(
                symbol=symbol_upper,
                name=name,
                asset_type=type_enum,
                purchase_price=purchase_price,
                quantity=quantity
            )
            
        self.items[symbol_upper] = item
        self.save_portfolio()
        logger.info(f"Successfully added holding: {symbol_upper} (Qty: {quantity}, Avg Price: {purchase_price})")
        return item

    def remove_holding(self, symbol: str) -> bool:
        """Removes an asset symbol completely from the tracked portfolio list."""
        symbol_upper = symbol.strip().upper()
        if symbol_upper in self.items:
            del self.items[symbol_upper]
            self.save_portfolio()
            logger.info(f"Removed holding: {symbol_upper}")
            return True
        logger.warning(f"Failed to remove: Symbol {symbol_upper} not found in holdings.")
        return False

    def clear_all(self) -> None:
        """Removes all items from the portfolio."""
        self.items = {}
        self.save_portfolio()
        logger.info("Cleared all holdings in the portfolio.")

    def get_valuation(self) -> PortfolioSummary:
        """
        Queries the MockFinancialDataService for active prices of all symbols,
        then tallies current valuations and profit/loss metrics.
        """
        valuations = []
        total_value = 0.0
        total_cost = 0.0

        for symbol, item in self.items.items():
            quote = self.market_service.get_quote(symbol)
            current_price = quote.price if quote else item.purchase_price
            
            cost = item.purchase_price * item.quantity
            value = current_price * item.quantity
            gain_loss = value - cost
            gain_percent = (gain_loss / cost * 100.0) if cost > 0 else 0.0
            
            valuations.append(ItemValuation(
                item=item,
                current_price=current_price,
                current_value=round(value, 2),
                total_cost=round(cost, 2),
                gain_loss=round(gain_loss, 2),
                gain_loss_percent=round(gain_percent, 2)
            ))
            
            total_value += value
            total_cost += cost

        overall_gain = total_value - total_cost
        overall_gain_percent = (overall_gain / total_cost * 100.0) if total_cost > 0 else 0.0

        return PortfolioSummary(
            total_value=round(total_value, 2),
            total_cost=round(total_cost, 2),
            overall_gain=round(overall_gain, 2),
            overall_gain_percent=round(overall_gain_percent, 2),
            valuations=valuations
        )

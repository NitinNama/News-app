import random
import time
from datetime import datetime, timedelta
from typing import Dict, Optional, List

from .models import Asset, AssetType, MarketQuote

# Baseline financial instrument parameters
SUPPORTED_ASSETS = {
    # Stocks
    "AAPL": Asset("AAPL", "Apple Inc.", AssetType.STOCK, base_price=175.50, precision=2),
    "MSFT": Asset("MSFT", "Microsoft Corp.", AssetType.STOCK, base_price=415.20, precision=2),
    "GOOG": Asset("GOOG", "Alphabet Inc.", AssetType.STOCK, base_price=152.80, precision=2),
    "TSLA": Asset("TSLA", "Tesla Inc.", AssetType.STOCK, base_price=170.10, precision=2),
    "NVDA": Asset("NVDA", "NVIDIA Corp.", AssetType.STOCK, base_price=875.00, precision=2),
    "AMZN": Asset("AMZN", "Amazon.com Inc.", AssetType.STOCK, base_price=178.40, precision=2),
    
    # Currencies & Crypto
    "EUR/USD": Asset("EUR/USD", "Euro / US Dollar", AssetType.CURRENCY, base_price=1.0850, precision=4),
    "GBP/USD": Asset("GBP/USD", "British Pound / US Dollar", AssetType.CURRENCY, base_price=1.2680, precision=4),
    "USD/JPY": Asset("USD/JPY", "US Dollar / Japanese Yen", AssetType.CURRENCY, base_price=151.40, precision=2),
    "USD/INR": Asset("USD/INR", "US Dollar / Indian Rupee", AssetType.CURRENCY, base_price=83.30, precision=4),
    "BTC/USD": Asset("BTC/USD", "Bitcoin / US Dollar", AssetType.CURRENCY, base_price=64250.00, precision=2)
}

class MockFinancialDataService:
    """
    Simulates real-time stock and currency market data.
    Uses a deterministic pseudorandom walk algorithm so that price movements
    fluctuate realistically but naturally trend around their baseline over time.
    """
    def __init__(self):
        self.assets = SUPPORTED_ASSETS.copy()
        # Keep track of active simulated prices
        self.current_prices: Dict[str, float] = {sym: asset.base_price for sym, asset in self.assets.items()}
        self.daily_highs: Dict[str, float] = {sym: asset.base_price * 1.02 for sym, asset in self.assets.items()}
        self.daily_lows: Dict[str, float] = {sym: asset.base_price * 0.98 for sym, asset in self.assets.items()}
        self.volumes: Dict[str, int] = {sym: random.randint(10000, 500000) for sym in self.assets.keys()}
        
        # Last tick state
        self.last_update_time = datetime.now()

    def simulate_tick(self) -> None:
        """
        Simulates standard micro-fluctuations (ticks) for all assets.
        Applies a random-walk algorithm with a mean-reverting pull toward base price.
        """
        now = datetime.now()
        time_elapsed = (now - self.last_update_time).total_seconds()
        
        # Only update if some time has passed to look realistic
        if time_elapsed < 0.1:
            return
            
        self.last_update_time = now
        
        for symbol, asset in self.assets.items():
            current_price = self.current_prices[symbol]
            base_price = asset.base_price
            
            # Mean reversion parameter (pulls price back to base slightly to prevent infinite runaway)
            reversion_speed = 0.02
            reversion_pull = (base_price - current_price) * reversion_speed
            
            # Random volatility (Stocks have higher percentage volatility than Currencies)
            volatility = 0.0015 if asset.asset_type == AssetType.STOCK else 0.0003
            if symbol == "BTC/USD":
                volatility = 0.003  # Crypto is extra volatile!
                
            percent_change = random.normalvariate(0, volatility)
            price_change = (current_price * percent_change) + reversion_pull
            
            # Calculate new price
            new_price = max(0.0001, current_price + price_change)
            
            # Apply precision rounding
            new_price = round(new_price, asset.precision)
            self.current_prices[symbol] = new_price
            
            # Update high/low
            if new_price > self.daily_highs[symbol]:
                self.daily_highs[symbol] = new_price
            if new_price < self.daily_lows[symbol]:
                self.daily_lows[symbol] = new_price
                
            # Increase volume slightly with each tick
            self.volumes[symbol] += random.randint(5, 100)

    def get_quote(self, symbol: str) -> Optional[MarketQuote]:
        """
        Simulates ticks, then returns the latest quote for the requested symbol.
        """
        symbol_upper = symbol.strip().upper()
        if symbol_upper not in self.assets:
            # Dynamically add custom assets so users can track untypical symbols!
            is_currency = "/" in symbol_upper or len(symbol_upper) >= 6 and symbol_upper.isalpha()
            asset_type = AssetType.CURRENCY if is_currency else AssetType.STOCK
            base = 100.0 if asset_type == AssetType.STOCK else 1.0
            
            self.assets[symbol_upper] = Asset(
                symbol=symbol_upper,
                name=f"Custom {symbol_upper} Asset",
                asset_type=asset_type,
                base_price=base,
                precision=4 if asset_type == AssetType.CURRENCY else 2
            )
            self.current_prices[symbol_upper] = base
            self.daily_highs[symbol_upper] = base * 1.01
            self.daily_lows[symbol_upper] = base * 0.99
            self.volumes[symbol_upper] = random.randint(1000, 10000)

        # Apply tick updates
        self.simulate_tick()
        
        asset = self.assets[symbol_upper]
        price = self.current_prices[symbol_upper]
        base_price = asset.base_price
        
        # Calculate changes relative to base price (simulating previous close)
        change = price - base_price
        change_percent = (change / base_price) * 100.0
        
        # Calculate bid-ask spreads (bid is slightly lower, ask slightly higher)
        spread_ratio = 0.001 if asset.asset_type == AssetType.STOCK else 0.0002
        if symbol_upper == "BTC/USD":
            spread_ratio = 0.0005
            
        spread = price * spread_ratio
        bid = round(price - (spread / 2), asset.precision)
        ask = round(price + (spread / 2), asset.precision)
        
        return MarketQuote(
            symbol=symbol_upper,
            price=price,
            change=round(change, asset.precision),
            change_percent=round(change_percent, 2),
            bid=bid,
            ask=ask,
            volume=self.volumes[symbol_upper],
            high=self.daily_highs[symbol_upper],
            low=self.daily_lows[symbol_upper],
            last_updated=datetime.now()
        )

    def get_all_supported_assets(self) -> List[Asset]:
        """Returns list of default simulated stocks and currency pairs."""
        return list(SUPPORTED_ASSETS.values())

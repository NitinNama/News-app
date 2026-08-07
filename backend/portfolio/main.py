import argparse
import sys
import os
import json
import time
import logging
from datetime import datetime

from .models import AssetType
from .market_data import MockFinancialDataService
from .portfolio_manager import PortfolioManager

def setup_logging(verbose: bool):
    """Sets up clean logger formatting."""
    level = logging.DEBUG if verbose else logging.WARNING
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
        handlers=[logging.StreamHandler(sys.stdout)]
    )

def print_banner():
    """Prints a styled financial dashboard header."""
    print("=" * 68)
    print("   🌐  PULSE FINANCIAL MARKET BACKEND & PORTFOLIO TRACKER  🌐   ")
    print("=" * 68)

def format_currency(val: float, is_currency_pair: bool = False) -> str:
    """Helper to cleanly format currency strings based on scale."""
    if is_currency_pair and val < 5:
        return f"${val:.4f}"
    return f"${val:,.2f}"

def show_market_prices(market: MockFinancialDataService):
    """Displays current quotes for all standard mock financial assets."""
    print("\n--- Current Financial Market Quotes ---")
    print(f"{'Symbol':<10} | {'Name':<24} | {'Price':<10} | {'Change (%)':<12}")
    print("-" * 68)
    
    for symbol in market.assets.keys():
        quote = market.get_quote(symbol)
        if not quote:
            continue
            
        color_start = "\033[92m" if quote.change >= 0 else "\033[91m"
        color_end = "\033[0m"
        
        is_fx = quote.symbol in ["EUR/USD", "GBP/USD", "USD/JPY", "USD/INR"]
        price_str = format_currency(quote.price, is_fx)
        
        sign = "+" if quote.change >= 0 else ""
        change_str = f"{sign}{quote.change_percent:.2f}%"
        
        # Strip color coding if the output is piped or not supporting ANSI colors
        if not sys.stdout.isatty():
            color_start = ""
            color_end = ""
            
        print(f"{quote.symbol:<10} | {market.assets[symbol].name:<24} | {price_str:<10} | {color_start}{change_str:<12}{color_end}")
    print("-" * 68)

def show_portfolio_report(manager: PortfolioManager):
    """Prints a beautiful summary report of the current holdings."""
    summary = manager.get_valuation()
    
    print("\n--- Your Asset Portfolio Summary ---")
    if not summary.valuations:
        print(" [!] Your portfolio is currently empty. Use --add to track your first asset.")
        print("-" * 68)
        return
        
    print(f"{'Symbol':<10} | {'Holdings':<8} | {'Avg Price':<10} | {'Current':<9} | {'Valuation':<11} | {'Profit/Loss':<11}")
    print("-" * 68)
    
    for val in summary.valuations:
        item = val.item
        is_fx = item.symbol in ["EUR/USD", "GBP/USD", "USD/JPY", "USD/INR"]
        
        color_start = "\033[92m" if val.gain_loss >= 0 else "\033[91m"
        color_end = "\033[0m"
        if not sys.stdout.isatty():
            color_start = ""
            color_end = ""
            
        sign = "+" if val.gain_loss >= 0 else ""
        pl_str = f"{sign}{format_currency(val.gain_loss)} ({sign}{val.gain_loss_percent:.1f}%)"
        
        print(f"{item.symbol:<10} | {item.quantity:<8.2f} | {format_currency(item.purchase_price, is_fx):<10} | {format_currency(val.current_price, is_fx):<9} | {format_currency(val.current_value):<11} | {color_start}{pl_str:<11}{color_end}")
        
    print("-" * 68)
    
    color_start = "\033[92m" if summary.overall_gain >= 0 else "\033[91m"
    color_end = "\033[0m"
    if not sys.stdout.isatty():
        color_start = ""
        color_end = ""
        
    sign = "+" if summary.overall_gain >= 0 else ""
    overall_pl = f"{sign}{format_currency(summary.overall_gain)} ({sign}{summary.overall_gain_percent:.2f}%)"
    
    print(f"Total Cost basis:   {format_currency(summary.total_cost)}")
    print(f"Current Valuation:  {format_currency(summary.total_value)}")
    print(f"Net Profit/Loss:    {color_start}{overall_pl}{color_end}")
    print("=" * 68)

def run_live_simulation(manager: PortfolioManager):
    """Runs a live-updating console tick monitor."""
    print("\nStarting live-updating portfolio dashboard.")
    print("Press Ctrl+C to stop simulation and return to terminal...\n")
    time.sleep(1.5)
    
    try:
        while True:
            # Clear standard Linux/Windows consoles cleanly
            os.system('cls' if os.name == 'nt' else 'clear')
            print_banner()
            
            # Simulate a continuous tick interval
            manager.market_service.simulate_tick()
            
            # Print market and portfolio statuses
            show_market_prices(manager.market_service)
            show_portfolio_report(manager)
            
            print(" [i] Updates occur every 3 seconds. Watch prices change real-time...")
            time.sleep(3.0)
    except KeyboardInterrupt:
        print("\n[!] Live simulation terminated.")

def main():
    parser = argparse.ArgumentParser(
        description="Pulse Backend Portfolio Module: Real-time stock tickers, currency pairs tracking, and mock valuation."
    )
    parser.add_argument(
        "--market", action="store_true", help="Print a snapshot of all simulated market assets."
    )
    parser.add_argument(
        "--portfolio", action="store_true", help="Print a detailed holdings valuation report."
    )
    parser.add_argument(
        "--add", nargs=5, metavar=("SYMBOL", "NAME", "STOCK|CURRENCY", "PRICE", "QUANTITY"),
        help="Add an asset holding. Args: Symbol, Name, Type (STOCK/CURRENCY), PurchasePrice, Quantity."
    )
    parser.add_argument(
        "--remove", type=str, metavar="SYMBOL", help="Remove an asset symbol from your portfolio."
    )
    parser.add_argument(
        "--clear", action="store_true", help="Completely clear all tracked holdings."
    )
    parser.add_argument(
        "--live", action="store_true", help="Launch a live-updating interactive market & portfolio simulation."
    )
    parser.add_argument(
        "--output", type=str, metavar="FILE", help="Save the portfolio summary output report as a JSON file."
    )
    parser.add_argument(
        "--verbose", action="store_true", help="Show verbose debug logs."
    )

    args = parser.parse_args()
    setup_logging(args.verbose)

    market_service = MockFinancialDataService()
    manager = PortfolioManager(market_service=market_service)

    # Print general header
    if sys.stdout.isatty() and not args.output:
        print_banner()

    # Process Commands
    action_taken = False

    if args.clear:
        manager.clear_all()
        print("[✓] Portfolio successfully cleared.")
        action_taken = True

    if args.remove:
        removed = manager.remove_holding(args.remove)
        if removed:
            print(f"[✓] Removed {args.remove.upper()} from your portfolio.")
        else:
            print(f"[!] Symbol {args.remove.upper()} was not found in holdings.")
        action_taken = True

    if args.add:
        sym, name, item_type, price_str, qty_str = args.add
        try:
            price = float(price_str)
            qty = float(qty_str)
            item_type = item_type.upper()
            if item_type not in ["STOCK", "CURRENCY"]:
                print("[!] Error: Type must be either 'STOCK' or 'CURRENCY'.", file=sys.stderr)
                sys.exit(1)
                
            item = manager.add_holding(sym, name, item_type, price, qty)
            print(f"[✓] Added holding: {item.quantity:.2f} shares/units of {item.symbol} ({item.name}) @ {format_currency(item.purchase_price)}")
            action_taken = True
        except ValueError:
            print("[!] Error: Price and Quantity parameters must be numbers.", file=sys.stderr)
            sys.exit(1)

    if args.live:
        run_live_simulation(manager)
        return

    # Snapshot outputs if live was not specified
    if args.market or (not action_taken and not args.portfolio and not args.output):
        show_market_prices(market_service)
        action_taken = True

    if args.portfolio or (not action_taken and not args.output):
        show_portfolio_report(manager)

    if args.output:
        summary = manager.get_valuation()
        try:
            with open(args.output, "w") as f:
                json.dump(summary.to_dict(), f, indent=2)
            print(f"\n[✓] Portfolio summary saved cleanly to {args.output}")
        except Exception as e:
            print(f"[!] Error: Failed to write to {args.output}: {e}", file=sys.stderr)

if __name__ == "__main__":
    main()

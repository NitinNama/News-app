package com.example.data.network

import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MarketQuote(
    val symbol: String,
    val name: String,
    val type: String,
    val currentPrice: Double,
    val changePercent: Double,
    val dailyHigh: Double,
    val dailyLow: Double
)

object MockFinancialService {
    private val random = Random()
    
    // Baseline references
    private val baseAssets = mapOf(
        "SENSEX" to Pair("BSE SENSEX", 79200.50),
        "NIFTY 50" to Pair("NSE NIFTY 50", 24150.80),
        "AAPL" to Pair("Apple Inc.", 175.50),
        "MSFT" to Pair("Microsoft Corp.", 415.20),
        "GOOG" to Pair("Alphabet Inc.", 152.80),
        "TSLA" to Pair("Tesla Inc.", 170.10),
        "NVDA" to Pair("NVIDIA Corp.", 875.00),
        "AMZN" to Pair("Amazon.com Inc.", 178.40),
        "EUR/USD" to Pair("Euro / US Dollar", 1.0850),
        "GBP/USD" to Pair("British Pound / US Dollar", 1.2680),
        "USD/JPY" to Pair("US Dollar / Japanese Yen", 151.40),
        "USD/INR" to Pair("US Dollar / Indian Rupee", 83.30),
        "BTC/USD" to Pair("Bitcoin / US Dollar", 64250.00)
    )

    private val prices = ConcurrentHashMap<String, Double>()
    private val highs = ConcurrentHashMap<String, Double>()
    private val lows = ConcurrentHashMap<String, Double>()
    
    private val _quotesFlow = MutableStateFlow<Map<String, MarketQuote>>(emptyMap())
    val quotesFlow: StateFlow<Map<String, MarketQuote>> = _quotesFlow.asStateFlow()

    init {
        // Initialize prices and bounds
        baseAssets.forEach { (symbol, pair) ->
            val basePrice = pair.second
            prices[symbol] = basePrice
            highs[symbol] = basePrice * 1.02
            lows[symbol] = basePrice * 0.98
        }
        updateQuotes()
    }

    /**
     * Simulates a tick for all assets using random-walk with a slight mean reversion.
     */
    fun simulateTick() {
        baseAssets.forEach { (symbol, pair) ->
            val currentPrice = prices[symbol] ?: pair.second
            val basePrice = pair.second
            
            // Mean reversion pull toward base price
            val reversionPull = (basePrice - currentPrice) * 0.02
            
            // Volatility scale: Crypto (BTC) has high, Stocks have medium, Forex has low
            val volatility = when {
                symbol == "BTC/USD" -> 0.003
                symbol.contains("/") -> 0.0003
                else -> 0.0015
            }
            
            val percentChange = random.nextGaussian() * volatility
            val priceChange = (currentPrice * percentChange) + reversionPull
            
            val precision = if (symbol.contains("/") && !symbol.startsWith("BTC")) 4 else 2
            val newPrice = Math.max(0.0001, currentPrice + priceChange)
            
            // Round to precision
            val roundedPrice = roundToDecimals(newPrice, precision)
            prices[symbol] = roundedPrice
            
            // Update highs/lows
            highs[symbol] = Math.max(highs[symbol] ?: roundedPrice, roundedPrice)
            lows[symbol] = Math.min(lows[symbol] ?: roundedPrice, roundedPrice)
        }
        updateQuotes()
    }

    /**
     * Retrieves or registers custom assets requested by the user dynamically.
     */
    fun getPrice(symbol: String, fallbackPrice: Double = 100.0): Double {
        val upperSymbol = symbol.trim().uppercase()
        val currentPrice = prices[upperSymbol]
        if (currentPrice != null) {
            return currentPrice
        }
        
        // Dynamically track custom symbols
        prices[upperSymbol] = fallbackPrice
        highs[upperSymbol] = fallbackPrice * 1.01
        lows[upperSymbol] = fallbackPrice * 0.99
        updateQuotes()
        return fallbackPrice
    }

    fun getQuote(symbol: String): MarketQuote? {
        val upperSymbol = symbol.trim().uppercase()
        return _quotesFlow.value[upperSymbol]
    }

    private fun updateQuotes() {
        val updatedMap = mutableMapOf<String, MarketQuote>()
        prices.forEach { (symbol, price) ->
            val basePair = baseAssets[symbol]
            val name = basePair?.first ?: "Custom Asset"
            val type = if (symbol.contains("/") || symbol == "BTC/USD") "CURRENCY" else "STOCK"
            val basePrice = basePair?.second ?: price
            
            val change = price - basePrice
            val changePercent = if (basePrice > 0) (change / basePrice) * 100.0 else 0.0
            
            updatedMap[symbol] = MarketQuote(
                symbol = symbol,
                name = name,
                type = type,
                currentPrice = price,
                changePercent = roundToDecimals(changePercent, 2),
                dailyHigh = highs[symbol] ?: price,
                dailyLow = lows[symbol] ?: price
            )
        }
        _quotesFlow.value = updatedMap
    }

    private fun roundToDecimals(value: Double, places: Int): Double {
        var multiplier = 1.0
        repeat(places) { multiplier *= 10.0 }
        return Math.round(value * multiplier) / multiplier
    }
}

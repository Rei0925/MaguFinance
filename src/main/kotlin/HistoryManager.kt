package com.github.rei0925

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StockHistoryEntry(
    val timestamp: Long,
    val companyName: String,
    val stockPrice: Double,
    val openPrice: Double = stockPrice,
    val highPrice: Double = stockPrice,
    val lowPrice: Double = stockPrice
)

@Serializable
data class AverageHistoryEntry(
    val timestamp: Long,
    val averagePrice: Double
)

object HistoryManager {
    private val historyFile = File(System.getProperty("user.dir"), "stock_history.json")
    private val json = Json { prettyPrint = true }

    private val history = mutableListOf<StockHistoryEntry>()

    fun load() {
        if (historyFile.exists()) {
            history.clear()
            history.addAll(json.decodeFromString(historyFile.readText()))
        }
    }

    fun save() {
        historyFile.writeText(json.encodeToString(history))
    }

    fun recordSnapshot() {
        val now = System.currentTimeMillis()
        CompanyManager.companyList.forEach { company ->
            history.add(StockHistoryEntry(now, company.name, company.stockPrice))
        }
        save()
    }

    fun getHistory(): List<StockHistoryEntry> = history.toList()

    fun getStockHistory(company: String): List<StockHistoryEntry> =
        history.filter { it.companyName == company }

    fun getAverageHistory(): List<AverageHistoryEntry> {
        return history
            .groupBy { it.timestamp }
            .map { (timestamp, entries) ->
                val avg = entries.map { it.stockPrice }.average()
                AverageHistoryEntry(timestamp, avg)
            }
            .sortedBy { it.timestamp }
    }
}
package com.github.rei0925.magufinance.manager

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

data class StockHistoryEntry(
    val timestamp: Long,
    val companyName: String,
    val stockPrice: Double,
    val openPrice: Double = stockPrice,
    val highPrice: Double = stockPrice,
    val lowPrice: Double = stockPrice
)

data class AverageHistoryEntry(
    val timestamp: Long,
    val averagePrice: Double
)

class HistoryManager(
    private val connection: Connection,
    private val companyManager: CompanyManager
) {
    init {
        // テーブルがなければ作成
        val stmt = connection.createStatement()
        stmt.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS stock_history (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                timestamp BIGINT NOT NULL,
                companyName VARCHAR(255) NOT NULL,
                stockPrice DOUBLE NOT NULL,
                openPrice DOUBLE NOT NULL,
                highPrice DOUBLE NOT NULL,
                lowPrice DOUBLE NOT NULL
            )
            """.trimIndent()
        )
        stmt.close()
    }

    fun recordSnapshot() {
        val now = System.currentTimeMillis()
        val sql = """
            INSERT INTO stock_history
            (timestamp, companyName, stockPrice, openPrice, highPrice, lowPrice)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        val pstmt: PreparedStatement = connection.prepareStatement(sql)
        val snapshot = companyManager.companyList.toList()
        snapshot.forEach { company ->
            pstmt.setLong(1, now)
            pstmt.setString(2, company.name)
            pstmt.setDouble(3, company.stockPrice)
            pstmt.setDouble(4, company.stockPrice)
            pstmt.setDouble(5, company.stockPrice)
            pstmt.setDouble(6, company.stockPrice)
            pstmt.addBatch()
        }
        pstmt.executeBatch()
        pstmt.close()
    }

    fun getHistory(): List<StockHistoryEntry> {
        val list = mutableListOf<StockHistoryEntry>()
        val stmt = connection.createStatement()
        val rs: ResultSet = stmt.executeQuery("SELECT timestamp, companyName, stockPrice, openPrice, highPrice, lowPrice FROM stock_history ORDER BY timestamp ASC")
        while (rs.next()) {
            list.add(
                StockHistoryEntry(
                    timestamp = rs.getLong("timestamp"),
                    companyName = rs.getString("companyName"),
                    stockPrice = rs.getDouble("stockPrice"),
                    openPrice = rs.getDouble("openPrice"),
                    highPrice = rs.getDouble("highPrice"),
                    lowPrice = rs.getDouble("lowPrice")
                )
            )
        }
        rs.close()
        stmt.close()
        return list
    }

    fun getStockHistory(company: String): List<StockHistoryEntry> {
        val list = mutableListOf<StockHistoryEntry>()
        val sql = "SELECT timestamp, companyName, stockPrice, openPrice, highPrice, lowPrice FROM stock_history WHERE companyName = ? ORDER BY timestamp ASC"
        val pstmt = connection.prepareStatement(sql)
        pstmt.setString(1, company)
        val rs = pstmt.executeQuery()
        while (rs.next()) {
            list.add(
                StockHistoryEntry(
                    timestamp = rs.getLong("timestamp"),
                    companyName = rs.getString("companyName"),
                    stockPrice = rs.getDouble("stockPrice"),
                    openPrice = rs.getDouble("openPrice"),
                    highPrice = rs.getDouble("highPrice"),
                    lowPrice = rs.getDouble("lowPrice")
                )
            )
        }
        rs.close()
        pstmt.close()
        return list
    }

    fun getAverageHistory(): List<AverageHistoryEntry> {
        val map = mutableMapOf<Long, MutableList<Double>>()
        val stmt = connection.createStatement()
        val rs = stmt.executeQuery("SELECT timestamp, stockPrice FROM stock_history")
        while (rs.next()) {
            val ts = rs.getLong("timestamp")
            val price = rs.getDouble("stockPrice")
            map.computeIfAbsent(ts) { mutableListOf() }.add(price)
        }
        rs.close()
        stmt.close()
        return map.map { (timestamp, prices) ->
            AverageHistoryEntry(timestamp, prices.average())
        }.sortedBy { it.timestamp }
    }
}
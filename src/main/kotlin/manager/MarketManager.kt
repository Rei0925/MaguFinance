package com.github.rei0925.magufinance.manager

import java.sql.Connection
import kotlin.random.Random

class MarketManager(
    private val connection: Connection,
    private val companyManager: CompanyManager
) {
    fun updateStockPrices(demandFactor: Double = 1.0, supplyFactor: Double = 1.0) {
        companyManager.companyList.forEach { company ->
            val ratio = demandFactor / supplyFactor
            val noise = Random.nextDouble(0.98, 1.02) // ランダム ±2%

            val newPrice = company.stockPrice * ratio * noise
            company.stockPrice = newPrice.toInt().coerceAtLeast(1).toDouble() // 株価は最低1円（整数）
        }
        companyManager.save()
    }

    fun showMarket() {
        println("=== 株式市場 ===")
        companyManager.companyList.forEach { company ->
            println("${company.name} : ${company.stockPrice.toInt()}円 " +
                    "(${company.availableStocks}/${company.totalStocks}株)")
        }
    }
}
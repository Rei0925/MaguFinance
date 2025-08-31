package com.github.rei0925

import kotlin.random.Random

object MarketManager {
    fun updateStockPrices(demandFactor: Double = 1.0, supplyFactor: Double = 1.0) {
        CompanyManager.companyList.forEach { company ->
            val ratio = demandFactor / supplyFactor
            val noise = Random.nextDouble(0.98, 1.02) // ランダム ±2%

            val newPrice = company.stockPrice * ratio * noise
            company.stockPrice = newPrice.toInt().coerceAtLeast(1).toDouble() // 株価は最低1円（整数）
        }
        CompanyManager.save()
    }

    fun showMarket() {
        println("=== 株式市場 ===")
        CompanyManager.companyList.forEach { company ->
            println("${company.name} : ${company.stockPrice.toInt()}円 " +
                    "(${company.availableStocks}/${company.totalStocks}株)")
        }
    }
}
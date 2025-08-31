package com.github.rei0925

import java.io.File
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.random.Random

@Serializable
data class Company(
    val name: String,
    var stockPrice: Double,
    val totalStocks: Int,
    var availableStocks: Int
)

object CompanyManager {
    private val jsonFile = File(System.getProperty("user.dir"), "company.json")
    private val json = Json { prettyPrint = true }
    // 変更後
    val companyList = mutableListOf<Company>()  // 名前を変更

    init {
        loadCompanies()
    }

    fun loadCompanies() {
        if (!jsonFile.exists()) {
            // resources/company.json をコピー
            val resource = this::class.java.getResource("/company.json")
                ?: error("company.json not found in resources")
            jsonFile.writeBytes(resource.readBytes())
        }

        companyList.clear()
        companyList.addAll(json.decodeFromString<List<Company>>(jsonFile.readText()))
    }

    fun loadOrCreate() {
        loadCompanies()
    }

    fun save() {
        jsonFile.writeText(json.encodeToString(companyList))
    }

    fun createCompany(name: String, stockPrice: Double, totalStocks: Int) {
        val newCompany = Company(name, stockPrice, totalStocks, totalStocks)
        companyList.add(newCompany)
        save()
        println("会社 $name を作成しました！")
    }

    fun showCompanies() {
        if (companyList.isEmpty()) {
            println("会社のリストは空です。")
            return
        }
        println("現在の会社一覧:")
        for (company in companyList) {
            println("会社名: ${company.name}, 株価: ${company.stockPrice}, 総株数: ${company.totalStocks}, 利用可能株数: ${company.availableStocks}")
        }
    }

    fun getCompanies(): List<Company> {
        return companyList.toList()
    }

    fun buyStock(companyName: String, qty: Int): Boolean {
        val company = companyList.find { it.name == companyName }
        if (company == null) {
            println("会社 $companyName は存在しません。")
            return false
        }
        if (qty <= 0) {
            println("購入株数は正の整数でなければなりません。")
            return false
        }
        if (company.availableStocks >= qty) {
            company.availableStocks -= qty
            updateStockPrices(company, qty)
            save()
            println("会社 $companyName の株を $qty 株購入しました。残りの株数: ${company.availableStocks}")
            return true
        } else {
            println("会社 $companyName の株の在庫が不足しています。現在の残株数: ${company.availableStocks}")
            return false
        }
    }

    fun sellStock(companyName: String, qty: Int): Boolean {
        val company = companyList.find { it.name == companyName }
        if (company == null) {
            println("会社 $companyName は存在しません。")
            return false
        }
        if (qty <= 0) {
            println("売却株数は正の整数でなければなりません。")
            return false
        }
        company.availableStocks += qty
        if (company.availableStocks > company.totalStocks) {
            company.availableStocks = company.totalStocks
        }
        updateStockPrices(company, -qty)
        save()
        println("会社 $companyName の株を $qty 株売却しました。現在の利用可能株数: ${company.availableStocks}")
        return true
    }

    fun applyEvent(companyName: String, percentageChange: Double) {
        val company = companyList.find { it.name == companyName }
        if (company == null) {
            println("会社 $companyName は存在しません。")
            return
        }
        val newPrice = company.stockPrice * (1 + percentageChange)
        company.stockPrice = newPrice.coerceAtLeast(1.0).toInt().toDouble()
        save()
        println("会社 $companyName の株価が変更されました。新しい株価: ${company.stockPrice}")
    }

    private fun updateStockPrices(company: Company, changeInAvailableStocks: Int = 0) {
        if (company.totalStocks <= 0) return

        // Scarcity effect: fewer available stocks -> higher price
        val scarcityRatio = (company.totalStocks - company.availableStocks).toDouble() / company.totalStocks
        var scarcityMultiplier = 1.0 + scarcityRatio * 0.01  // 最大5%上昇まで

        // Demand/Supply effect: positive for buy, negative for sale
        val demandSupplyEffect = if (changeInAvailableStocks >= 0) {
            // Buying: price goes up slightly
            1.0 + (changeInAvailableStocks.toDouble() / company.totalStocks) * 0.1
        } else {
            // Selling: price goes down slightly
            1.0 + (changeInAvailableStocks.toDouble() / company.totalStocks) * -0.1
        }
        scarcityMultiplier *= demandSupplyEffect

        // Random market noise: ±5%
        val marketNoise = Random.nextDouble(0.95, 1.05)

        // Small trend: slight drift based on previous price
        val trend = 1.0 + Random.nextDouble(-0.01, 0.01)

        val newPrice = company.stockPrice * scarcityMultiplier * marketNoise * trend

        company.stockPrice = newPrice.coerceAtLeast(1.0).toInt().toDouble()
    }

    fun reloadCompanies() {
        loadCompanies()
    }

    fun smallEvent() {
        if (companyList.isEmpty()) return
        val index = Random.nextInt(companyList.size)
        val company = companyList[index]
        val percent = Random.nextDouble(-0.02, 0.02)
        company.stockPrice = (company.stockPrice * (1 + percent)).coerceAtLeast(1.0).toInt().toDouble()
        save()
    }
}

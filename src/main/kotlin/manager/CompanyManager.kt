package com.github.rei0925.magufinance.manager

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.random.Random

data class Company(
    val id: Int, // 会社ID
    val name: String,
    var stockPrice: Double,
    val totalStocks: Int,
    var availableStocks: Int
)

class CompanyManager(
    private val connection: Connection
) {
    val companyList = mutableListOf<Company>()

    private val logger = LoggerFactory.getLogger(CompanyManager::class.java)

    init {
        // テーブルがなければ作成
        val stmt = connection.createStatement()
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS companies (
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(255) NOT NULL UNIQUE,
                stockPrice DOUBLE NOT NULL,
                totalStocks INT NOT NULL,
                availableStocks INT NOT NULL
            )
        """.trimIndent())
        stmt.close()

        loadCompanies()
    }

    fun loadCompanies() {
        companyList.clear()
        val stmt = connection.createStatement()
        val rs: ResultSet = stmt.executeQuery("SELECT id, name, stockPrice, totalStocks, availableStocks FROM companies")
        while (rs.next()) {
            val company = Company(
                id = rs.getInt("id"),
                name = rs.getString("name"),
                stockPrice = rs.getDouble("stockPrice"),
                totalStocks = rs.getInt("totalStocks"),
                availableStocks = rs.getInt("availableStocks")
            )
            companyList.add(company)
        }
        rs.close()
        stmt.close()
    }

    fun loadOrCreate() {
        loadCompanies()
    }

    fun save() {
        val sql = """
            INSERT INTO companies (id, name, stockPrice, totalStocks, availableStocks) 
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
                name = VALUES(name),
                stockPrice = VALUES(stockPrice), 
                totalStocks = VALUES(totalStocks),
                availableStocks = VALUES(availableStocks)
        """.trimIndent()
        val pstmt: PreparedStatement = connection.prepareStatement(sql)
        for (company in companyList) {
            pstmt.setInt(1, company.id)
            pstmt.setString(2, company.name)
            pstmt.setDouble(3, company.stockPrice)
            pstmt.setInt(4, company.totalStocks)
            pstmt.setInt(5, company.availableStocks)
            pstmt.addBatch()
        }
        pstmt.executeBatch()
        pstmt.close()
    }

    fun createCompany(name: String, stockPrice: Double, totalStocks: Int) {
        val newId = (companyList.maxOfOrNull { it.id } ?: 0) + 1
        val newCompany = Company(newId, name, stockPrice, totalStocks, totalStocks)
        companyList.add(newCompany)
        save()
        logger.info("会社 $name (ID: $newId) を作成しました！")
    }

    fun showCompanies() {
        if (companyList.isEmpty()) {
            println("会社のリストは空です。")
            return
        }
        println("現在の会社一覧:")
        for (company in companyList) {
            println("会社ID: ${company.id}, 会社名: ${company.name}, 株価: ${company.stockPrice}, 総株数: ${company.totalStocks}, 利用可能株数: ${company.availableStocks}")
        }
    }

    fun getCompanies(): List<Company> {
        loadCompanies() // DBから最新情報を読み込む
        return companyList.toList()
    }

    fun checkCompany(companyName: String) : Boolean{
        val company = companyList.find { it.name == companyName }
        return company != null
    }

    fun getCompany(companyName: String) : Company? {
        return companyList.find { it.name == companyName }
    }

    fun getCompanyById(id: Int): Company? {
        return companyList.find { it.id == id }
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
            logger.info("会社 $companyName の株を $qty 株購入しました。残りの株数: ${company.availableStocks}")
            return true
        } else {
            logger.info("会社 $companyName の株の在庫が不足しています。現在の残株数: ${company.availableStocks}")
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
        logger.info("会社 $companyName の株を $qty 株売却しました。現在の利用可能株数: ${company.availableStocks}")
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
        logger.info("会社 $companyName の株価が変更されました。新しい株価: ${company.stockPrice}")
    }

    private fun updateStockPrices(company: Company, changeInAvailableStocks: Int = 0) {
        if (company.totalStocks <= 0) return

        val scarcityRatio = (company.totalStocks - company.availableStocks).toDouble() / company.totalStocks
        var scarcityMultiplier = 1.0 + scarcityRatio * 0.01

        val demandSupplyEffect = if (changeInAvailableStocks >= 0) {
            1.0 + (changeInAvailableStocks.toDouble() / company.totalStocks) * 0.1
        } else {
            1.0 + (changeInAvailableStocks.toDouble() / company.totalStocks) * -0.1
        }
        scarcityMultiplier *= demandSupplyEffect

        val marketNoise = Random.nextDouble(0.95, 1.05)
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
        logger.info("株価を更新しました")
    }
}

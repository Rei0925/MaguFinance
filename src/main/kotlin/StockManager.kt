package com.github.rei0925

import com.github.rei0925.manager.CompanyManager
import java.sql.Connection

data class PlayerStock(
    val companyId: Int,
    val amount: Int
)

class StockManager(
    private val connection: Connection,
    private val companyManager: CompanyManager
) {

    init {
        // テーブルが存在しなければ作成
        val stmt = connection.createStatement()
        stmt.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS player_stocks (
                player_id BIGINT NOT NULL,
                company_id INT NOT NULL,
                amount INT NOT NULL DEFAULT 0,
                PRIMARY KEY (player_id, company_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """.trimIndent()
        )
        stmt.close()
    }

    fun getOwnedStocks(playerId: Long): List<PlayerStock> {
        val stmt = connection.prepareStatement(
            "SELECT company_id, amount FROM player_stocks WHERE player_id = ? AND amount > 0"
        )
        stmt.setLong(1, playerId)
        val rs = stmt.executeQuery()
        val ownedStocks = mutableListOf<PlayerStock>()
        while (rs.next()) {
            ownedStocks.add(PlayerStock(rs.getInt("company_id"), rs.getInt("amount")))
        }
        rs.close()
        stmt.close()
        return ownedStocks
    }

    fun getStock(playerId: Long, companyId: Int): PlayerStock? {
        val stmt = connection.prepareStatement(
            "SELECT amount FROM player_stocks WHERE player_id = ? AND company_id = ?"
        )
        stmt.setLong(1, playerId)
        stmt.setInt(2, companyId)
        val rs = stmt.executeQuery()
        val stock = if (rs.next()) PlayerStock(companyId, rs.getInt("amount")) else null
        rs.close()
        stmt.close()
        return stock
    }

    fun buy(playerId: Long, companyId: Int, amount: Int): PlayerStock? {
        if (amount <= 0) return null
        val company = companyManager.getCompanyById(companyId) ?: return null

        val currentAmount = getStock(playerId, companyId)?.amount ?: 0
        val newAmount = currentAmount + amount
        saveToDB(playerId, companyId, newAmount)
        return PlayerStock(companyId, newAmount)
    }

    fun sell(playerId: Long, companyId: Int, amount: Int): PlayerStock? {
        if (amount <= 0) return null
        val currentAmount = getStock(playerId, companyId)?.amount ?: return null
        if (currentAmount < amount) return null

        val newAmount = currentAmount - amount
        saveToDB(playerId, companyId, newAmount)
        return PlayerStock(companyId, newAmount)
    }

    private fun saveToDB(playerId: Long, companyId: Int, amount: Int) {
        val stmt = connection.prepareStatement(
            """
            INSERT INTO player_stocks (player_id, company_id, amount)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE amount = ?
            """.trimIndent()
        )
        stmt.setLong(1, playerId)
        stmt.setInt(2, companyId)
        stmt.setInt(3, amount)
        stmt.setInt(4, amount)
        stmt.executeUpdate()
        stmt.close()
    }

    fun loadPlayer(playerId: Long): List<PlayerStock> {
        val stmt = connection.prepareStatement(
            "SELECT company_id, amount FROM player_stocks WHERE player_id = ?"
        )
        stmt.setLong(1, playerId)
        val rs = stmt.executeQuery()
        val stocks = mutableListOf<PlayerStock>()
        while (rs.next()) {
            stocks.add(PlayerStock(rs.getInt("company_id"), rs.getInt("amount")))
        }
        rs.close()
        stmt.close()
        return stocks
    }
}
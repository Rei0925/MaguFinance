package com.github.rei0925.manager

import java.sql.Connection

class BankManager(
    private val connection: Connection
) {

    init {
        val stmt = connection.createStatement()
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS bank_accounts (
                user_id BIGINT PRIMARY KEY,
                balance DOUBLE NOT NULL,
                onFreeze BOOLEAN NOT NULL DEFAULT FALSE
            )
        """.trimIndent())
        stmt.close()
    }

    // アカウント作成
    fun createAccount(userId: Long, initialBalance: Double = 50000.0) {
        val sql = """
            INSERT INTO bank_accounts (user_id, balance, onFreeze)
            VALUES (?, ?, FALSE)
            ON DUPLICATE KEY UPDATE balance = balance
        """.trimIndent()
        val pstmt = connection.prepareStatement(sql)
        pstmt.setLong(1, userId)
        pstmt.setDouble(2, initialBalance)
        pstmt.executeUpdate()
        pstmt.close()
    }

    // 所持金追加
    fun addBalance(userId: Long, amount: Double) {
        val sql = "UPDATE bank_accounts SET balance = balance + ? WHERE user_id = ? AND onFreeze = FALSE"
        val pstmt = connection.prepareStatement(sql)
        pstmt.setDouble(1, amount)
        pstmt.setLong(2, userId)
        pstmt.executeUpdate()
        pstmt.close()
    }

    // 所持金取り上げ
    fun removeBalance(userId: Long, amount: Double) {
        val sql = "UPDATE bank_accounts SET balance = balance - ? WHERE user_id = ? AND balance >= ? AND onFreeze = FALSE"
        val pstmt = connection.prepareStatement(sql)
        pstmt.setDouble(1, amount)
        pstmt.setLong(2, userId)
        pstmt.setDouble(3, amount)
        pstmt.executeUpdate()
        pstmt.close()
    }

    // アカウントフリーズ
    fun freezeAccount(userId: Long, freeze: Boolean = true) {
        val sql = "UPDATE bank_accounts SET onFreeze = ? WHERE user_id = ?"
        val pstmt = connection.prepareStatement(sql)
        pstmt.setBoolean(1, freeze)
        pstmt.setLong(2, userId)
        pstmt.executeUpdate()
        pstmt.close()
    }

    // アカウントの残高取得
    fun getBalance(userId: Long): Double {
        val sql = "SELECT balance FROM bank_accounts WHERE user_id = ?"
        val pstmt = connection.prepareStatement(sql)
        pstmt.setLong(1, userId)
        val rs = pstmt.executeQuery()
        val balance = if (rs.next()) rs.getDouble("balance") else -1.0
        rs.close()
        pstmt.close()
        return balance
    }

    // 残高ランキングを返す
    fun getBalanceRanking(limit: Int = 10): List<Pair<Long, Double>> {
        val sql = "SELECT user_id, balance FROM bank_accounts ORDER BY balance DESC LIMIT ?"
        val pstmt = connection.prepareStatement(sql)
        pstmt.setInt(1, limit)
        val rs = pstmt.executeQuery()
        val list = mutableListOf<Pair<Long, Double>>()
        while (rs.next()) {
            list.add(rs.getLong("user_id") to rs.getDouble("balance"))
        }
        rs.close()
        pstmt.close()
        return list
    }
}
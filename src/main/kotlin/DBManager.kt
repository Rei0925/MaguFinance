package com.github.rei0925

import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DBManager() {

    private val dotenv = dotenv()
    private val url = dotenv["DB_ADDRESS"] ?: error("DB_ADDRESS not found in env")
    private val user = dotenv["DB_USER"] ?: error("DB_USER not found in env")
    private val password = dotenv["DB_PASSWORD"] ?: error("DB_PASSWORD not found in env")

    private var connection: Connection? = null

    private val logger = LoggerFactory.getLogger(DBManager::class.java)

    fun connect(): Connection {
        if (connection == null || connection!!.isClosed) {
            try {
                connection = DriverManager.getConnection(url, user, password)
                logger.info("DB接続成功")
            } catch (e: SQLException) {
                logger.error("DB接続エラー: ${e.message}")
                throw e
            }
        }
        return connection!!
    }

    fun disconnect() {
        try {
            connection?.close()
            logger.info("DB切断完了")
        } catch (e: SQLException) {
            logger.error("DB切断エラー: ${e.message}")
        } finally {
            connection = null
        }
    }
}
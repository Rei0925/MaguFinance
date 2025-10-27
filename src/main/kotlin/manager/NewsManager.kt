package com.github.rei0925.magufinance.manager

import kotlinx.serialization.Serializable
import java.sql.Connection

@Serializable
data class News(val genre: String, val content: String)

class NewsManager(private val connection: Connection) {
    private val newsList = mutableListOf<News>()
    private var currentIndex = 0

    init {
        // テーブルがなければ作成
        val stmt = connection.createStatement()
        stmt.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS news (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                genre VARCHAR(255) NOT NULL,
                content TEXT NOT NULL
            )
            """.trimIndent()
        )
        stmt.close()

        loadNews()
    }

    private fun loadNews() {
        newsList.clear()
        val stmt = connection.createStatement()
        val rs = stmt.executeQuery("SELECT genre, content FROM news ORDER BY id ASC")
        while (rs.next()) {
            newsList.add(News(rs.getString("genre"), rs.getString("content")))
        }
        rs.close()
        stmt.close()
    }

    fun getAllNews(): List<News> {
        return newsList
    }

    fun getNextNews(): News? {
        if (newsList.isEmpty()) return null
        val news = newsList[currentIndex]
        currentIndex = (currentIndex + 1) % newsList.size
        return news
    }

    fun addNews(genre: String, content: String) {
        val sql = "INSERT INTO news (genre, content) VALUES (?, ?)"
        val pstmt = connection.prepareStatement(sql)
        pstmt.setString(1, genre)
        pstmt.setString(2, content)
        pstmt.executeUpdate()
        pstmt.close()
        newsList.add(News(genre, content))
    }

    fun removeNews(id: Long) {
        val sql = "DELETE FROM news WHERE id = ?"
        val pstmt = connection.prepareStatement(sql)
        pstmt.setLong(1, id)
        pstmt.executeUpdate()
        pstmt.close()
        // 再読み込みして同期
        loadNews()
    }
}
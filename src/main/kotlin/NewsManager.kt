package com.github.rei0925

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class News(val genre: String, val content: String)

object NewsManager {
    private val newsFile = File("NEWS.json")
    private var newsList: List<News> = emptyList()
    private var currentIndex = 0

    init {
        if (newsFile.exists()) {
            val text = newsFile.readText()
            newsList = Json.decodeFromString(ListSerializer(News.serializer()), text)
        } else {
            this::class.java.getResourceAsStream("/NEWS.json").use { inputStream ->
                inputStream?.let {
                    newsFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    val text = newsFile.readText()
                    newsList = Json.decodeFromString(ListSerializer(News.serializer()), text)
                }
            }
        }
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
}
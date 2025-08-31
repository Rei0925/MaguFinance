package com.github.rei0925

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

class NewsTicker private constructor(
    private val newsList: List<News> = NewsManager.getAllNews(),
    private val textColor: Color = Color.BLACK,
    private val backgroundColor: Color = Color.WHITE
) {
    private val frame: JFrame = JFrame("ニュース速報")
    private val tickerPanel = TickerPanel().apply {
        font = Font("SansSerif", Font.BOLD, 16)
        foreground = textColor
        background = backgroundColor
        isOpaque = true
        preferredSize = Dimension(0, 30)
    }
    private var tickerTimer: Timer? = null
    private var currentNewsIndex = 0
    private var running = false

    init {
        frame.layout = java.awt.BorderLayout()
        frame.add(tickerPanel, java.awt.BorderLayout.CENTER)
        frame.contentPane.background = backgroundColor
        tickerPanel.background = backgroundColor
        frame.defaultCloseOperation = JFrame.HIDE_ON_CLOSE
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = false
    }

    private fun startTicker() {
        if (newsList.isEmpty()) return
        val x = frame.width
        val concatenatedNews = newsList.joinToString("     ") { "【${it.genre}】 ${it.content}" }
        tickerPanel.setText(concatenatedNews, textColor)
        tickerPanel.xPos = x
        tickerPanel.repaint()
        val fps = 60
        tickerTimer = Timer((1000 / fps)) {
            tickerPanel.xPos -= 2
            if (tickerPanel.xPos + tickerPanel.textWidth < 0) {
                tickerPanel.xPos = frame.width
            }
            tickerPanel.repaint()
        }
        tickerTimer?.start()
    }

    private fun stopTicker() {
        tickerTimer?.stop()
        tickerTimer = null
    }

    fun show() {
        if (!running) {
            frame.isVisible = true
            startTicker()
            running = true
        }
    }

    fun hide() {
        if (running) {
            stopTicker()
            frame.isVisible = false
            running = false
        }
    }

    companion object {
        private var instance: NewsTicker? = null

        /**
         * Start the news ticker with an optional list of News.
         * If newsList is not provided, it will be loaded from NewsManager.
         * @param newsList List of News to display (optional).
         * @param textColor Color for news text (default: white).
         * @param backgroundColor Background color (default: black).
         */
        fun start(
            newsList: List<News>? = null,
            textColor: Color = Color.BLACK,
            backgroundColor: Color = Color.WHITE
        ) {
            stop()
            instance = if (newsList != null) {
                NewsTicker(newsList, textColor, backgroundColor)
            } else {
                NewsTicker(NewsManager.getAllNews(), textColor, backgroundColor)
            }
            SwingUtilities.invokeLater {
                instance?.show()
            }
        }

        /**
         * Stop and hide the news ticker window.
         */
        fun stop() {
            instance?.hide()
            instance = null
        }
    }

    private class TickerPanel : JPanel() {
        var textParts: List<Pair<String, Color>> = emptyList()
            private set
        var xPos: Int = 0
        var textWidth: Int = 0
        private var fontMetrics: FontMetrics? = null

        fun setText(text: String, color: Color = Color.WHITE) {
            textParts = listOf(text to color)
            fontMetrics = getFontMetrics(font)
            textWidth = textParts.sumOf { fontMetrics?.stringWidth(it.first) ?: 0 }
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            g.font = font
            if (fontMetrics == null) {
                fontMetrics = g.fontMetrics
                textWidth = textParts.sumOf { fontMetrics?.stringWidth(it.first) ?: 0 }
            }
            val y = (height + (fontMetrics?.ascent ?: 0) - (fontMetrics?.descent ?: 0)) / 2
            var currentX = xPos
            for ((part, color) in textParts) {
                g.color = color
                val width = fontMetrics?.stringWidth(part) ?: 0
                g.drawString(part, currentX, y)
                currentX += width
            }
        }
    }
}
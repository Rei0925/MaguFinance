package com.github.rei0925.magufinance

import com.github.rei0925.magufinance.manager.CompanyManager
import com.github.rei0925.magufinance.manager.HistoryManager
import com.github.rei0925.magufinance.manager.News
import com.github.rei0925.magufinance.manager.NewsManager
import com.github.rei0925.magufinance.manager.TickerSyncManager
import java.awt.*
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities


/**
 * UnifiedTicker displays a ticker bar that alternates between stock prices and news,
 * mimicking the RealTimeChart4 ticker layout, color logic, and scroll behavior.
 */
class UnifiedTicker private constructor(
    private val newsManager: NewsManager,
    private val historyManager: HistoryManager,
    private val companyManager: CompanyManager,
    private val newsList: List<News> = newsManager.getAllNews(),
    private val textColor: Color = Color.WHITE,
    private val backgroundColor: Color = Color.BLACK
) {
    private val frame: JFrame = JFrame("リアルタイムティッカー")
    private val tickerPanel = TickerPanel().apply {
        font = Font("SansSerif", Font.BOLD, 16)
        foreground = textColor
        background = backgroundColor
        isOpaque = true
        preferredSize = Dimension(0, 30)
    }
    private var running = false
    private var tickerMode = "stocks" // "stocks" or "news"
    private var snapshotPrices: Map<String, Int> = emptyMap()
    private var snapshotAvg: Int = 0

    init {
        frame.layout = BorderLayout()
        val tickerContainer = JPanel(BorderLayout())
        tickerContainer.background = backgroundColor
        tickerContainer.add(tickerPanel, BorderLayout.CENTER)
        frame.add(tickerContainer, BorderLayout.CENTER)
        frame.contentPane.background = backgroundColor
        tickerPanel.background = backgroundColor
        frame.defaultCloseOperation = JFrame.HIDE_ON_CLOSE
        frame.setSize(1920, 50)
        frame.setLocationRelativeTo(null)
        frame.isVisible = false
    }

    // Builds the stock ticker text with color based on price change (diffs from snapshotPrices).
    private fun buildStockText(): List<Pair<String, Color>> {
        val parts = mutableListOf<Pair<String, Color>>()
        // 国内平均
        val avg = historyManager.getAverageHistory().lastOrNull()?.averagePrice?.toInt() ?: snapshotAvg
        val avgDiff = avg - snapshotAvg
        val avgColor = when {
            avgDiff > 0 -> Color.RED
            avgDiff < 0 -> Color.GREEN
            else -> Color.GRAY
        }
        val avgArrow = when {
            avgDiff > 0 -> "+$avgDiff"
            avgDiff < 0 -> "-${-avgDiff}"
            else -> "0"
        }
        parts.add("国内平均 $avg 円 " to Color.WHITE)
        parts.add("$avgArrow " to avgColor)
        parts.add("｜" to Color.WHITE)
        // 各社
        companyManager.companyList.forEachIndexed { index, company ->
            val oldPrice = snapshotPrices[company.name] ?: company.stockPrice.toInt()
            val diff = company.stockPrice.toInt() - oldPrice
            val diffColor = when {
                diff > 0 -> Color.RED
                diff < 0 -> Color.GREEN
                else -> Color.GRAY
            }
            val mainText = " ${company.name} ${company.stockPrice.toInt()}円 "
            val diffText = if (diff != 0) (if (diff > 0) "+${kotlin.math.abs(diff)}" else "-${kotlin.math.abs(diff)}") else "0"
            parts.add(mainText to Color.WHITE)
            parts.add(diffText to diffColor)
            if (index < companyManager.companyList.size - 1) {
                parts.add("｜" to Color.WHITE)
            }
        }
        return parts
    }

    // Builds the news ticker text for all news, separated by spaces.
    private fun buildNewsText(): List<Pair<String, Color>> {
        val parts = mutableListOf<Pair<String, Color>>()
        for ((index, news) in newsList.withIndex()) {
            val part = "   【${news.genre}】 ${news.content}"
            parts.add(part to textColor)
            if (index != newsList.lastIndex) {
                parts.add("     " to textColor)
            }
        }
        return parts
    }

    private var tickerPanelInitialized = false

    private fun startTicker() {
        if (companyManager.companyList.isEmpty() && newsList.isEmpty()) return
        val x = frame.width
        snapshotPrices = companyManager.companyList.associate { it.name to it.stockPrice.toInt() }
        snapshotAvg = historyManager.getAverageHistory().lastOrNull()?.averagePrice?.toInt() ?: 0
        tickerMode = "stocks"

        // 初回表示は一度だけ
        if (!tickerPanelInitialized) {
            tickerPanelInitialized = true
            tickerPanel.setTextParts(buildStockText())
            tickerPanel.xPos = x
            tickerPanel.repaint()
        }

        tickerPanel.onScrollEnd = {
            SwingUtilities.invokeLater {
                if (tickerMode == "stocks") {
                    tickerPanel.setTextParts(buildNewsText())
                    tickerMode = "news"
                } else {
                    snapshotPrices = companyManager.companyList.associate { it.name to it.stockPrice.toInt() }
                    snapshotAvg = historyManager.getAverageHistory().lastOrNull()?.averagePrice?.toInt() ?: 0
                    tickerPanel.setTextParts(buildStockText())
                    tickerMode = "stocks"
                }
                tickerPanel.xPos = frame.width
                tickerPanel.repaint()
            }
        }

        TickerSyncManager.registerNewsTicker(tickerPanel)
        TickerSyncManager.start(tickerPanel)
    }

    private fun stopTicker() {
        TickerSyncManager.stop(tickerPanel)
        TickerSyncManager.unregisterStockTicker()
        tickerPanel.onScrollEnd = null
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
        private var instance: UnifiedTicker? = null

        /**
         * Start the ticker window.
         * @param newsList Optional news list to display (default: NewsManager.getAllNews()).
         * @param textColor Foreground color (default: white).
         * @param backgroundColor Background color (default: black).
         */
        fun start(
            newsList: List<News>? = null,
            textColor: Color = Color.WHITE,
            backgroundColor: Color = Color.BLACK
        ) {
            stop()
            instance = UnifiedTicker(newsManager,historyManager,companyManager,newsList ?: newsManager.getAllNews(), textColor, backgroundColor)
            SwingUtilities.invokeLater {
                instance?.show()
            }
        }

        /**
         * Stop and hide the ticker window.
         */
        fun stop() {
            instance?.hide()
            instance = null
        }
    }

    class TickerPanel : JPanel() {
        var textParts: List<Pair<String, Color>> = emptyList()
            private set
        var xPos: Int = 0
        var textWidth: Int = 0
        private var fontMetrics: FontMetrics? = null
        var onScrollEnd: (() -> Unit)? = null

        fun setTextParts(parts: List<Pair<String, Color>>) {
            textParts = parts
            fontMetrics = getFontMetrics(font)
            textWidth = textParts.sumOf { fontMetrics?.stringWidth(it.first) ?: 0 } +
                (if (textParts.size > 1) (textParts.size - 1) * (fontMetrics?.stringWidth(" ") ?: 0) else 0)
        }

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
                textWidth = textParts.sumOf { fontMetrics?.stringWidth(it.first) ?: 0 } +
                    (if (textParts.size > 1) (textParts.size - 1) * (fontMetrics?.stringWidth(" ") ?: 0) else 0)
            }
            val y = (height + (fontMetrics?.ascent ?: 0) - (fontMetrics?.descent ?: 0)) / 2
            var currentX = xPos
            for ((i, partColor) in textParts.withIndex()) {
                val (part, color) = partColor
                g.color = color
                val width = fontMetrics?.stringWidth(part) ?: 0
                g.drawString(part, currentX, y)
                currentX += width
                // Add a space between parts except after last
                if (i < textParts.size - 1) {
                    val spaceWidth = fontMetrics?.stringWidth(" ") ?: 0
                    currentX += spaceWidth
                }
            }
            // When text fully scrolled past left edge, trigger onScrollEnd
            if (textWidth > 0 && currentX < 0) {
                onScrollEnd?.invoke()
            }
        }
    }
}
package com.github.rei0925

import org.knowm.xchart.XChartPanel
import org.knowm.xchart.XYChart
import org.knowm.xchart.XYChartBuilder
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.util.Date
import java.util.concurrent.Executors
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.collections.flatMap

class RealTimeChart4(
    private val companyManager: CompanyManager,
    private val historyManager: HistoryManager,
    private val newsManager: NewsManager
) {

    private val tickerPanel = TickerPanel().apply {
        font = Font("SansSerif", Font.BOLD, 16)
        foreground = Color.WHITE
        background = Color.BLACK
        isOpaque = true
        preferredSize = Dimension(0, 30)
    }
    private val tickerExecutor = Executors.newSingleThreadScheduledExecutor()

    private var chart3CompanyName: String = ""
    private var chart4CompanyName: String = ""

    private val frame: JFrame = JFrame("4分割チャート").apply {
        background = Color.BLACK
    }
    private val chart1: XYChart
    private val chart2: XYChart
    private val chart3: XYChart
    private val chart4: XYChart
    private val charts: List<XYChart>
    private val panels: List<XChartPanel<XYChart>>
    private var timer: Timer? = null
    private var companyCycleTimer: Timer? = null
    private val companyColors = mutableMapOf<String, Color>()

    private var tickerMode = "stocks" // "stocks"か"news"

    private var newsQueue: List<News> = emptyList()

    private var newsIndex = 0

    private val availableColors = listOf(
        Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.ORANGE, Color.CYAN,
        Color.PINK, Color.YELLOW, Color.GRAY, Color.DARK_GRAY
    )

    init {
        frame.layout = java.awt.BorderLayout()
        val chartsPanel = JPanel(java.awt.GridLayout(2, 2))

        chart1 = XYChartBuilder().width(960).height(540).title("全社").build()
        chart2 = XYChartBuilder().width(960).height(540).title("国内平均").build()
        chart3 = XYChartBuilder().width(960).height(540).title(chart3CompanyName).build()
        chart4 = XYChartBuilder().width(960).height(540).title(chart4CompanyName).build()

        charts = listOf(chart1, chart2, chart3, chart4)
        panels = charts.map { XChartPanel(it) }

        panels.forEach { chartsPanel.add(it) }
        frame.add(chartsPanel, java.awt.BorderLayout.CENTER)

        val tickerContainer = JPanel(java.awt.BorderLayout())
        tickerContainer.add(tickerPanel, java.awt.BorderLayout.CENTER)
        frame.add(tickerContainer, java.awt.BorderLayout.SOUTH)

        frame.contentPane.background = Color.BLACK
        chartsPanel.background = Color.BLACK
        tickerContainer.background = Color.BLACK
        tickerPanel.background = Color.BLACK

        frame.defaultCloseOperation = JFrame.HIDE_ON_CLOSE
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }

    fun start() {
        if (timer == null) {
            timer = Timer(5000) {
                updateData()
            }
            timer?.start()
        }
        if (companyCycleTimer == null) {
            val companies = companyManager.companyList.map { it.name }
            val companyPairs = companies.chunked(2)
            var currentPairIndex = 0

            companyCycleTimer = Timer(10000) {
                val pair = companyPairs.getOrNull(currentPairIndex) ?: emptyList()

                chart3.seriesMap.clear()
                chart4.seriesMap.clear()

                if (pair.isNotEmpty()) {
                    updateSingleCompany(chart3, pair[0])
                    panels[2].isVisible = true
                } else {
                    panels[2].isVisible = false
                }

                if (pair.size > 1) {
                    updateSingleCompany(chart4, pair[1])
                    panels[3].isVisible = true
                } else {
                    panels[3].isVisible = false
                }

                panels[2].repaint()
                panels[3].repaint()

                currentPairIndex = (currentPairIndex + 1) % companyPairs.size
            }
            companyCycleTimer?.start()
        }
        startTicker()
        frame.isVisible = true
    }

    fun stop() {
        timer?.stop()
        companyCycleTimer?.stop()
        stopTicker()
        timer = null
        companyCycleTimer = null
        frame.isVisible = false
        tickerExecutor.shutdownNow()
    }

    private fun updateData() {
        tickerExecutor.submit {
            // chart1: 全社
            val companies = companyManager.companyList
            // Prepare data for all companies and for average
            val chart1SeriesData = mutableListOf<Triple<String, List<Date>, List<Number>>>()
            val chart1Colors = mutableMapOf<String, Color>()
            val allCompanyNames = (companies.map { it.name } + listOf(chart3CompanyName, chart4CompanyName)).distinct()
            allCompanyNames.forEach { name ->
                val entries = historyManager.getStockHistory(name).takeLast(50)
                if (entries.isNotEmpty()) {
                    val xData = entries.map { Date(it.timestamp) }
                    val yData = entries.map { it.stockPrice }
                    chart1SeriesData.add(Triple(name, xData, yData))
                    val idx = allCompanyNames.indexOf(name)
                    val color = availableColors[idx % availableColors.size]
                    chart1Colors[name] = color
                }
            }
            // Average series for chart1 and chart2
            val avgEntries = historyManager.getAverageHistory().takeLast(50)
            val avgXData = avgEntries.map { Date(it.timestamp) }
            val avgYData = avgEntries.map { it.averagePrice }
            val avgColor = Color.MAGENTA
            // chart2: 国内平均
            val chart2SeriesData = if (avgEntries.isNotEmpty()) Triple("国内平均", avgXData, avgYData) else null

            // Find min/max for y-axis scaling for chart1 and chart2
            val chart1YMin = if (avgYData.isNotEmpty()) avgYData.minOrNull() else null
            val chart1YMax = if (avgYData.isNotEmpty()) avgYData.maxOrNull() else null

            SwingUtilities.invokeLater {
                chart1.seriesMap.clear()
                companyColors.clear()
                chart1SeriesData.forEach { (name, xData, yData) ->
                    val series = chart1.addSeries(name, xData, yData)
                    val color = chart1Colors[name] ?: availableColors[0]
                    companyColors[name] = color
                    series.lineColor = color
                    series.markerColor = color
                }
                // Add average series to chart1
                if (avgXData.isNotEmpty() && avgYData.isNotEmpty()) {
                    val avgSeries = chart1.addSeries("国内平均", avgXData, avgYData)
                    avgSeries.lineColor = avgColor
                    avgSeries.markerColor = avgColor
                    companyColors["国内平均"] = avgColor
                    if (chart1YMin != null && chart1YMax != null) {
                        chart1.styler.yAxisMin = chart1YMin * 0.98
                        chart1.styler.yAxisMax = chart1YMax * 1.02
                    }
                }
                // chart2: 国内平均
                chart2.seriesMap.clear()
                if (chart2SeriesData != null) {
                    val (name, xData, yData) = chart2SeriesData
                    val avgSeries = chart2.addSeries(name, xData, yData)
                    avgSeries.lineColor = avgColor
                    avgSeries.markerColor = avgColor
                    companyColors["国内平均"] = avgColor
                    if (chart1YMin != null && chart1YMax != null) {
                        chart2.styler.yAxisMin = chart1YMin * 0.98
                        chart2.styler.yAxisMax = chart1YMax * 1.02
                    }
                }
                if (!companyColors.containsKey("国内平均")) {
                    companyColors["国内平均"] = avgColor
                }
                adjustYAxis(chart1)
                adjustYAxis(chart2)
                panels[0].repaint()
                panels[1].repaint()
            }
        }
    }

    private fun updateSingleCompany(chart: XYChart, companyName: String) {
        tickerExecutor.submit {
            val entries = historyManager.getStockHistory(companyName).takeLast(50)
            val xData = entries.map { Date(it.timestamp) }
            val yData = entries.map { it.stockPrice }
            val color = companyColors[companyName] ?: availableColors[0]
            SwingUtilities.invokeLater {
                chart.seriesMap.clear()
                chart.title = companyName
                if (entries.isNotEmpty()) {
                    val series = chart.addSeries(companyName, xData, yData)
                    series.lineColor = color
                    series.markerColor = color
                }
                adjustYAxis(chart)
                // Find which panel this chart belongs to and repaint
                val idx = charts.indexOf(chart)
                if (idx in panels.indices) {
                    panels[idx].repaint()
                }
            }
        }
    }

    private fun adjustYAxis(chart: XYChart) {
        val allY = chart.seriesMap.values.flatMap { series -> series.yData.map { it } }
        if (allY.isNotEmpty()) {
            val minY = allY.minOrNull()!! * 0.95
            val maxY = allY.maxOrNull()!! * 1.05
            chart.styler.yAxisMin = kotlin.math.ceil(minY)
            chart.styler.yAxisMax = kotlin.math.ceil(maxY)
        }
    }

    private fun startTicker() {
        val x = frame.width
        val snapshotPrices = companyManager.companyList.associate { it.name to it.stockPrice.toInt() }.toMutableMap()
        val snapshotAvg = historyManager.getAverageHistory().lastOrNull()?.averagePrice?.toInt() ?: 0
        tickerMode = "stocks"
        tickerExecutor.submit {
            val textParts = buildStockText(snapshotPrices, snapshotAvg)
            SwingUtilities.invokeLater {
                tickerPanel.setText(textParts)
                tickerPanel.xPos = x
                tickerPanel.repaint()
            }
        }
        tickerPanel.onScrollEnd = {
            when (tickerMode) {
                "stocks" -> {
                    tickerExecutor.submit {
                        val newTextParts = buildStockText(snapshotPrices, snapshotAvg)
                        val newNewsQueue = newsManager.getAllNews()
                        SwingUtilities.invokeLater {
                            tickerPanel.setText(newTextParts)
                            tickerPanel.xPos = frame.width
                            tickerPanel.repaint()
                            newsQueue = newNewsQueue
                            newsIndex = 0
                            tickerMode = if (newsQueue.isNotEmpty()) "news" else "stocks"
                        }
                    }
                }
                "news" -> {
                    tickerExecutor.submit {
                        if (newsQueue.isNotEmpty()) {
                            val concatenatedNews = newsQueue.joinToString(" \u00A0\u00A0\u00A0\u00A0\u00A0 ") { news -> buildNewsText(news) }
                            SwingUtilities.invokeLater {
                                tickerPanel.setText(concatenatedNews, Color.WHITE)
                                tickerPanel.xPos = frame.width
                                tickerPanel.repaint()
                                tickerMode = "stocks"
                            }
                        } else {
                            SwingUtilities.invokeLater {
                                tickerPanel.setText("")
                                tickerPanel.xPos = frame.width
                                tickerPanel.repaint()
                                tickerMode = "stocks"
                            }
                        }
                    }
                }
            }
        }
        TickerSyncManager.registerStockTicker(tickerPanel)
        TickerSyncManager.start(tickerPanel)
    }

    private fun stopTicker() {
        TickerSyncManager.stop(tickerPanel)
        TickerSyncManager.unregisterStockTicker()
        tickerPanel.onScrollEnd = null
    }

    private fun  buildStockText(snapshotPrices: Map<String, Int>, snapshotAvg: Int): List<Pair<String, Color>> {
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
        val parts = mutableListOf<Pair<String, Color>>()
        parts.add("国内平均 $avg 円 " to Color.WHITE)
        parts.add("$avgArrow " to avgColor)
        parts.add("｜" to Color.WHITE)

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
    private fun buildNewsText(news: News): String {
        return "   【${news.genre}】 ${news.content}"
    }

    companion object {
        private var instance: RealTimeChart4? = null

        fun start(companyManager: CompanyManager,historyManager: HistoryManager,newsManager: NewsManager) {
            if (instance == null) {
                instance = RealTimeChart4(companyManager,historyManager,newsManager)
                instance?.start()
            }
        }

        fun stop() {
            instance?.stop()
            instance = null
        }
    }

    class TickerPanel : JPanel() {
        var textParts: List<Pair<String, Color>> = emptyList()
        var xPos: Int = 0
        var textWidth: Int = 0
        private var fontMetrics: FontMetrics? = null
        var onScrollEnd: (() -> Unit)? = null

        fun setText(textParts: List<Pair<String, Color>>) {
            this.textParts = textParts
            fontMetrics = getFontMetrics(font)
            textWidth = textParts.sumOf { fontMetrics?.stringWidth(it.first) ?: 0 } +
                    (textParts.size - 1) * (fontMetrics?.stringWidth(" ") ?: 0)
        }

        fun setText(text: String, color: Color = Color.WHITE) {
            textParts = listOf(text to color)
            fontMetrics = getFontMetrics(font)
            textWidth = textParts.sumOf { fontMetrics?.stringWidth(it.first) ?: 0 } +
                    (textParts.size - 1) * (fontMetrics?.stringWidth(" ") ?: 0)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            g.font = font
            if (fontMetrics == null) {
                fontMetrics = g.fontMetrics
                textWidth = textParts.sumOf { fontMetrics?.stringWidth(it.first) ?: 0 } + (textParts.size - 1) * (fontMetrics?.stringWidth(" ") ?: 0)
            }
            val y = (height + fontMetrics!!.ascent - fontMetrics!!.descent) / 2
            var currentX = xPos
            for ((part, color) in textParts) {
                g.color = color
                val width = fontMetrics?.stringWidth(part) ?: 0
                g.drawString(part, currentX, y)
                currentX += width
                // Add space width after each part except last
                if (part != textParts.last().first) {
                    val spaceWidth = fontMetrics?.stringWidth(" ") ?: 0
                    currentX += spaceWidth
                }
            }
        }
    }
}

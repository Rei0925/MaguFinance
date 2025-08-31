package com.github.rei0925

import org.knowm.xchart.XYChart
import org.knowm.xchart.XYChartBuilder
import org.knowm.xchart.style.markers.SeriesMarkers
import org.knowm.xchart.SwingWrapper
import org.knowm.xchart.XChartPanel
import java.awt.Color
import javax.swing.JFrame
import javax.swing.Timer

class RealTimeChart {

    private val maxEntries = 50
    private val chart: XYChart = XYChartBuilder()
        .width(800)
        .height(600)
        .title("Real-Time Stock Prices - 全社表示")
        .xAxisTitle("日時")
        .yAxisTitle("株価")
        .build()

    private val frame: JFrame = JFrame("Real-Time Stock Prices - 全社表示")
    private val swingWrapper: SwingWrapper<XYChart>
    private val timer: Timer

    private val companyColors = mutableMapOf<String, Color>()
    private val availableColors = listOf(
        Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.ORANGE, Color.CYAN,
        Color.PINK, Color.YELLOW, Color.GRAY, Color.DARK_GRAY
    )

    init {
        chart.styler.isLegendVisible = true
        chart.styler.datePattern = "HH:mm:ss"
        chart.styler.axisTickLabelsFont = chart.styler.axisTickLabelsFont.deriveFont(12f)
        chart.styler.axisTitleFont = chart.styler.axisTitleFont.deriveFont(14f)

        frame.defaultCloseOperation = JFrame.HIDE_ON_CLOSE
        frame.contentPane.add(XChartPanel(chart))
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        swingWrapper = SwingWrapper(chart)

        timer = Timer(3000) { updateData() }
        timer.start()
    }

    private fun updateData() {
        chart.seriesMap.clear()
        val companies = CompanyManager.companyList
        var colorIndex = 0

        for (company in companies) {
            val entries = HistoryManager.getStockHistory(company.name)
            val latestEntries = if (entries.size > maxEntries) {
                entries.takeLast(maxEntries)
            } else {
                entries
            }
            if (latestEntries.isEmpty()) continue

            val xData = latestEntries.map { java.util.Date(it.timestamp) }
            val yData = latestEntries.map { it.stockPrice }

            val seriesName = company.name

            val series = chart.addSeries(seriesName, xData, yData)

            // Assign color
            val color = companyColors.getOrPut(company.name) {
                availableColors[colorIndex % availableColors.size].also { colorIndex++ }
            }
            series.lineColor = color
            series.markerColor = color
            series.marker = SeriesMarkers.CIRCLE
        }
        frame.repaint()
    }

    companion object {
        private var instance: RealTimeChart? = null

        fun start() {
            instance = RealTimeChart()
        }

        fun stop() {
            instance?.timer?.stop()
            instance?.frame?.isVisible = false
            instance = null
        }
    }
}
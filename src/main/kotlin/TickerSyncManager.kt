package com.github.rei0925

import javax.swing.Timer

object TickerSyncManager {
    var stockTickerPanel: RealTimeChart4.TickerPanel? = null
    var tickerPanel: UnifiedTicker.TickerPanel? = null
    private var timer: Timer? = null
    private var runningPanels: MutableSet<Any> = mutableSetOf()
    // Both panels must register themselves here to be scrolled.
    private val scrollSpeed = 2
    private const val FPS = 144

    fun registerStockTicker(panel: RealTimeChart4.TickerPanel) {
        stockTickerPanel = panel
    }
    fun registerNewsTicker(panel: UnifiedTicker.TickerPanel) {
        tickerPanel = panel
    }
    fun unregisterStockTicker() {
        stockTickerPanel = null
    }
    fun unregisterNewsTicker() {
        tickerPanel = null
    }
    fun start(panel: Any) {
        runningPanels.add(panel)
        if (timer == null) {
            timer = Timer((1000 / FPS)) {
                var repaintNeeded = false
                stockTickerPanel?.let { p ->
                    if (runningPanels.contains(p)) {
                        p.xPos -= scrollSpeed
                        if (p.xPos + p.textWidth < 0) {
                            p.xPos = p.parent?.width ?: p.width
                            p.onScrollEnd?.invoke()
                        }
                        repaintNeeded = true
                    }
                }
                tickerPanel?.let { p ->
                    if (runningPanels.contains(p)) {
                        p.xPos -= scrollSpeed
                        if (p.xPos + p.textWidth < 0) {
                            p.xPos = p.parent?.width ?: p.width
                            p.onScrollEnd?.invoke()
                        }
                        repaintNeeded = true
                    }
                }
                if (repaintNeeded) {
                    stockTickerPanel?.repaint()
                    tickerPanel?.repaint()
                }
            }
            timer?.start()
        }
    }
    fun stop(panel: Any) {
        runningPanels.remove(panel)
        if (runningPanels.isEmpty()) {
            timer?.stop()
            timer = null
        }
    }
}
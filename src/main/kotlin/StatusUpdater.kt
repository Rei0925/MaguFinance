package com.github.rei0925.magufinance

import com.github.rei0925.magufinance.manager.CompanyManager
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Activity
import kotlin.concurrent.fixedRateTimer
import java.util.*

class StatusUpdater(private val jda: JDA, private val companyManager: CompanyManager) {
    private var companyIndex = 0
    private var timer: Timer? = null

    fun start() {
        if (timer != null) return // 既に動いてたら無視

        timer = fixedRateTimer("StatusUpdater", daemon = true, initialDelay = 5000L, period = 5000L) {
            if (!jda.status.name.equals("CONNECTED", ignoreCase = true)) {
                stop()
                return@fixedRateTimer
            }

            val idx = (companyIndex + 1) % (1 + 1 + companyManager.getCompanies().size)
            companyIndex = idx

            val newStatus = when (idx) {
                0 -> "MaguFinance｜企業システム"
                1 -> {
                    val companies = companyManager.getCompanies()
                    if (companies.isEmpty()) {
                        "国内平均｜データなし"
                    } else {
                        val avg = companies.map { it.stockPrice }.average()
                        "国内平均｜${"%.2f".format(avg)}円"
                    }
                }
                else -> {
                    val companies = companyManager.getCompanies()
                    if (companies.isEmpty()) {
                        "会社情報なし"
                    } else {
                        val company = companies[(idx - 2) % companies.size]
                        "${company.name}｜${"%.2f".format(company.stockPrice)}円"
                    }
                }
            }

            jda.presence.activity = Activity.playing(newStatus)
        }
    }

    fun stop() {
        timer?.cancel()
        timer = null
    }

    fun restart() {
        stop()
        start()
    }
}
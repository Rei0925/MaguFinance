package com.github.rei0925

import io.github.cdimascio.dotenv.dotenv
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.reader.impl.history.DefaultHistory
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random
import kotlin.system.exitProcess

lateinit var statusUpdater: Timer
lateinit var stockHistoryTimer: Timer

fun main() {
    CompanyManager.loadCompanies() // 追加: 起動時に会社情報を読み込み

    val token = dotenv()["DISCORD_TOKEN"] ?: error("Token not found")
    val jda = JDABuilder.createDefault(token)
        .setActivity(Activity.playing("MaguSystem｜MaguFinanceを起動中"))
        .addEventListeners(SlashCommandListener())
        .build()
    jda.awaitReady()
    println("Bot ログイン完了！")

    // スラッシュコマンド登録
    jda.updateCommands()
        .addCommands(
            Commands.slash("stok-price", "株価を表示します"),
            Commands.slash("stok-history", "株価の履歴を表示します")
                .addOption(OptionType.STRING, "company", "会社名を指定すると、その会社の履歴だけ表示します", false),
            Commands.slash("broad-cast-ch", "MaguFinanceのお知らせを投稿するチャンネルを指定します")
                .addOption(OptionType.CHANNEL, "channel", "チャンネルを指定してください")
        )
        .queue()

    // ステータス更新
    jda.presence.activity = Activity.playing("開発｜まぐシステム")

    var companyIndex = 0
    statusUpdater = fixedRateTimer("StatusUpdater", daemon = true, initialDelay = 5000L, period = 5000L) {
        if (!jda.status.name.equals("CONNECTED", ignoreCase = true)) {
            this.cancel()
            return@fixedRateTimer
        }
        val idx = (companyIndex + 1) % (1 + 1 + CompanyManager.getCompanies().size)
        companyIndex = idx
        val newStatus = when (idx) {
            0 -> "MaguFinance｜企業システム"
            1 -> {
                val companies = CompanyManager.getCompanies()
                if (companies.isEmpty()) {
                    "国内平均｜データなし"
                } else {
                    val avg = companies.map { it.stockPrice }.average()
                    "国内平均｜${"%.2f".format(avg)}円"
                }
            }
            else -> {
                val companies = CompanyManager.getCompanies()
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

    stockHistoryTimer = fixedRateTimer("stockHistoryTimer", daemon = true, initialDelay = 0, period = /*1 * 60*/5 * 1000) {
        HistoryManager.recordSnapshot()
    }

    fun scheduleSmallEvent() {
        val delay = Random.nextLong(5_000L, 30_000L)
        Timer(true).schedule(object : java.util.TimerTask() {
            override fun run() {
                CompanyManager.smallEvent()
                scheduleSmallEvent()
            }
        }, delay)
    }
    scheduleSmallEvent()

    println("終了するには 'end' と入力してください")

    // JLine3 セットアップ
    val completer = StringsCompleter("end", "co", "co", "status", "market", "debug", "debug", "debug", "reload")
    val reader = LineReaderBuilder.builder()
        .completer(completer)
        .history(DefaultHistory())
        .build()

    mainLoop@ while (true) {
        val line = try {
            reader.readLine("> ")?.trim() ?: continue
        } catch (_: Exception) {
            continue
        }
        if (line.isEmpty()) continue

        val tokens = line.split("\\s+".toRegex())
        val command = tokens.getOrNull(0)?.lowercase() ?: continue

        when (command) {
            "end" -> {
                println("Bot を終了します...")

                // ステータスを一時的に変更
                jda.presence.activity = Activity.playing("MaguFinance｜システム終了中")

                // 5秒待機
                /** Thread.sleep(5000) **/

                // 各 Timer を停止とシャットダウン処理をtry-catchで囲む
                try {
                    statusUpdater.cancel()
                } catch (e: Exception) {
                    println("statusUpdater.cancel() でエラー: ${e.message}")
                }
                try {
                    stockHistoryTimer.cancel()
                } catch (e: Exception) {
                    println("stockHistoryTimer.cancel() でエラー: ${e.message}")
                }
                try {
                    RealTimeChart4.stop()
                    UnifiedTicker.stop()
                } catch (e: Exception) {
                    println("RealTimeChart4.stop() でエラー: ${e.message}")
                }
                try {
                    RealTimeChart.stop()
                } catch (e: Exception) {
                    println("RealTimeChart.stop() でエラー: ${e.message}")
                }
                try {
                    jda.shutdown()
                    jda.awaitShutdown()
                } catch (e: Exception) {
                    println("JDA shutdown でエラー: ${e.message}")
                }

                println("RealTimeChartシャットダウン完了")
                println("BOTシャットダウン完了")
                exitProcess(0)
            }
            "co" -> {
                val subcommand = tokens.getOrNull(1)?.lowercase()
                if (subcommand == "create") {
                    print("会社名: ")
                    val name = reader.readLine()?.trim() ?: continue@mainLoop
                    print("初期株価: ")
                    val priceInput = reader.readLine()?.trim()
                    val price = priceInput?.toDoubleOrNull() ?: run {
                        println("無効な株価です")
                        continue@mainLoop
                    }
                    print("発行株数: ")
                    val totalInput = reader.readLine()?.trim()
                    val total = totalInput?.toIntOrNull() ?: run {
                        println("無効な発行株数です")
                        continue@mainLoop
                    }
                    CompanyManager.createCompany(name, price, total)
                } else {
                    println("不明な co サブコマンド: ${subcommand ?: ""}")
                }
            }
            "status" -> println("現在のBotステータス: ${jda.presence.activity?.name ?: "なし"}")
            "market" -> {
                val demand = Random.nextDouble(0.8, 1.2)   // 需要
                val supply = Random.nextDouble(0.8, 1.2)   // 供給

                MarketManager.updateStockPrices(demand, supply)
                MarketManager.showMarket()
            }
            "debug" -> {
                when (tokens.getOrNull(1)?.lowercase()) {
                    "buy" -> tokens.getOrNull(2)?.let { tokens.getOrNull(3)?.let { it1 -> CompanyManager.buyStock(it, it1.toInt()) } }
                    "sell" -> tokens.getOrNull(2)?.let { tokens.getOrNull(3)?.let { it1 -> CompanyManager.sellStock(it, it1.toInt()) } }
                    "show" -> CompanyManager.showCompanies()
                    else -> println("不明な debug サブコマンド: ${tokens.getOrNull(1) ?: ""}")
                }
            }
            "reload" -> {
                jda.presence.activity = Activity.playing("国税庁｜再起動中")
                CompanyManager.reloadCompanies()
                println("情報を再読み込みしました")
            }
            "realtime" -> {
                when (tokens.getOrNull(1)?.lowercase()) {
                    "start" -> RealTimeChart.start()
                    "stop" -> RealTimeChart.stop()
                    else -> println("使い方: realtime start|stop")
                }
            }
            "realtime4" -> {
                when (tokens.getOrNull(1)?.lowercase()) {
                    "start" -> RealTimeChart4.start()
                    "stop" -> RealTimeChart4.stop()
                    else -> println("使い方: realtime4 start|stop")
                }
            }
            "ticker" -> {
                when (tokens.getOrNull(1)?.lowercase()) {
                    "start" -> UnifiedTicker.start()
                    "start2" -> {
                        RealTimeChart4.start()
                        UnifiedTicker.start()
                    }
                    "stop" -> UnifiedTicker.stop()
                    else -> println("使い方: ticker start|stop")
                }
            }
            else -> println("不明なコマンド: $line")
        }
    }
}
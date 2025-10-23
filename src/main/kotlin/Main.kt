package com.github.rei0925

import com.github.rei0925.command.ButtonCommand
import com.github.rei0925.command.CommandContext
import com.github.rei0925.command.CommandManager
import com.github.rei0925.command.CompanyCommand
import com.github.rei0925.command.EndCommand
import com.github.rei0925.command.MaintenanceCommand
import com.github.rei0925.command.Realtime4Command
import com.github.rei0925.command.RealtimeCommand
import com.github.rei0925.command.ReloadCommand
import com.github.rei0925.command.TickerCommand
import io.github.cdimascio.dotenv.dotenv
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random

lateinit var statusUpdater: Timer
lateinit var stockHistoryTimer: Timer
lateinit var broadCast: BroadCast
lateinit var companyManager: CompanyManager
lateinit var historyManager: HistoryManager
lateinit var marketManager: MarketManager
lateinit var newsManager: NewsManager
lateinit var bankManager: BankManager

fun main() {
    val dbManager = DBManager()      // インスタンス化
    val connection = dbManager.connect()
    companyManager = CompanyManager(connection)
    historyManager = HistoryManager(connection, companyManager)
    marketManager = MarketManager(connection, companyManager)
    newsManager = NewsManager(connection)
    bankManager = BankManager(connection)
    
    companyManager.loadCompanies() // 追加: 起動時に会社情報を読み込み

    val token = dotenv()["DISCORD_TOKEN"] ?: error("Token not found")
    val jda = JDABuilder.createDefault(token)
        .setActivity(Activity.playing("MaguSystem｜MaguFinanceを起動中"))
        .build()
    jda.awaitReady()
    println("Bot ログイン完了！")

    // スラッシュコマンド登録
    jda.updateCommands()
        .addCommands(
            Commands.slash("stok-price", "株価を表示します"),
            Commands.slash("stok-history", "株価の履歴を表示します")
                .addOption(OptionType.STRING, "company", "会社名を指定すると、その会社の履歴だけ表示します", false),
            Commands.slash("broad-cast", "MaguFinanceのお知らせを投稿するチャンネルを指定します")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
                .addOption(OptionType.CHANNEL, "channel", "チャンネルを指定してください", true),
            Commands.slash("atm", "ATM機能を使用します")
                .addSubcommands(
                    SubcommandData("balance", "自分の残高を確認します"),
                    SubcommandData("balance-top", "残高ランキングを表示します")
                )
        )
        .queue()

    broadCast = BroadCast(jda,connection,bankManager)

    jda.addEventListener(SlashCommandListener(jda,companyManager,historyManager,broadCast,bankManager))
    jda.addEventListener(broadCast)

    // ステータス更新
    jda.presence.activity = Activity.playing("開発｜まぐシステム")

    var companyIndex = 0
    statusUpdater = fixedRateTimer("StatusUpdater", daemon = true, initialDelay = 5000L, period = 5000L) {
        if (!jda.status.name.equals("CONNECTED", ignoreCase = true)) {
            this.cancel()
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

    stockHistoryTimer = fixedRateTimer("stockHistoryTimer", daemon = true, initialDelay = 0, period = /*1 * 60*/5 * 1000) {
        historyManager.recordSnapshot()
    }

    fun scheduleSmallEvent() {
        val delay = Random.nextLong(5_000L, 30_000L)
        Timer(true).schedule(object : TimerTask() {
            override fun run() {
                companyManager.smallEvent()
                scheduleSmallEvent()
            }
        }, delay)
    }
    scheduleSmallEvent()

    println("終了するには 'end' と入力してください")

    val reader = System.`in`.bufferedReader()

    val commandContext = CommandContext(
        jda,
        dbManager,
        reader,
        companyManager,
        marketManager,
        historyManager,
        newsManager,
        broadCast,
        statusUpdater,
        stockHistoryTimer,
        bankManager
    )

    val manager = CommandManager()
    manager.registerCommand(CompanyCommand(commandContext))
    manager.registerCommand(EndCommand(commandContext))
    manager.registerCommand(MaintenanceCommand(commandContext))
    manager.registerCommand(ReloadCommand(commandContext))
    manager.registerCommand(RealtimeCommand(commandContext))
    manager.registerCommand(Realtime4Command(commandContext))
    manager.registerCommand(TickerCommand(commandContext))
    manager.registerCommand(ButtonCommand(commandContext))

    while (true) {
        print("> ")
        val input = readlnOrNull()?.trim() ?: continue
        manager.runCommand(input)
    }
}
package com.github.rei0925.magufinance

import com.github.rei0925.magufinance.api.FinanceAPI
import com.github.rei0925.magufinance.api.FinanceAPIImpl
import com.github.rei0925.magufinance.api.FinancePlugin
import com.github.rei0925.magufinance.command.*
import com.github.rei0925.kotlincli.commands.CommandManager
import com.github.rei0925.magufinance.manager.BankManager
import com.github.rei0925.magufinance.manager.CompanyManager
import com.github.rei0925.magufinance.manager.HistoryManager
import com.github.rei0925.magufinance.manager.MarketManager
import com.github.rei0925.magufinance.manager.NewsManager
import com.github.rei0925.magufinance.manager.PluginManager
import io.github.cdimascio.dotenv.dotenv
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random

lateinit var stockHistoryTimer: Timer
lateinit var broadCast: BroadCast
lateinit var companyManager: CompanyManager
lateinit var historyManager: HistoryManager
lateinit var marketManager: MarketManager
lateinit var newsManager: NewsManager
lateinit var bankManager: BankManager
lateinit var stockManager: StockManager
lateinit var statusUpdater: StatusUpdater
lateinit var pluginManager: PluginManager
lateinit var financeAPIImpl: FinanceAPIImpl

val logger: Logger = LoggerFactory.getLogger("MaguFinance")

fun createPluginsFolder() {
    val pluginsDir = File("plugins")
    if (!pluginsDir.exists()) {
        val created = pluginsDir.mkdirs()
        if (created) {
            println("pluginsフォルダを作成しました")
        } else {
            println("pluginsフォルダの作成に失敗しました")
        }
    }
}

fun main() {
    createPluginsFolder()
    val dbManager = DBManager()      // インスタンス化
    val connection = dbManager.connect()
    companyManager = CompanyManager(connection)
    historyManager = HistoryManager(connection, companyManager)
    marketManager = MarketManager(connection, companyManager)
    newsManager = NewsManager(connection)
    bankManager = BankManager(connection)
    stockManager = StockManager(connection,companyManager)

    companyManager.loadCompanies() // 追加: 起動時に会社情報を読み込み

    val token = dotenv()["DISCORD_TOKEN"] ?: error("Token not found")
    val jda = JDABuilder.createDefault(token)
        .setActivity(Activity.playing("MaguSystem｜MaguFinanceを起動中"))
        .build()
    jda.awaitReady()
    logger.info("Bot ログイン完了！")
    statusUpdater = StatusUpdater(jda, companyManager)

    financeAPIImpl = FinanceAPIImpl(jda,dbManager,companyManager,marketManager,historyManager,newsManager,bankManager)
    pluginManager = PluginManager(financeAPIImpl)
    pluginManager.loadPlugins()


    val statusCh = jda.getVoiceChannelById(dotenv()["STATUS_CH"] ?: error("STATUS_CH not found"))
        ?: error("VoiceChannel not found for given ID")

    // スラッシュコマンド登録
    jda.updateCommands()
        .addCommands(
            Commands.slash("stock", "株システムを使用します")
                .addSubcommands(
                    SubcommandData("history", "株価の履歴を表示します")
                        .addOption(OptionType.STRING, "company", "会社名を指定すると、その会社の履歴だけ表示します", false),
                    SubcommandData("buy", "株を購入します")
                        .addOption(OptionType.STRING, "company", "株を購入する会社を指定してください", true, true)
                        .addOption(OptionType.INTEGER, "pieces", "購入する株の数を指定してください", true),
                    SubcommandData("sell", "株を売却します")
                        .addOption(OptionType.STRING, "company", "株を売却する会社を指定してください", true, true)
                        .addOption(OptionType.INTEGER, "pieces", "売却する株の数を指定してください", true),
                    SubcommandData("show", "所持している株を全表示します")
                ),
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
    val slashCommandListener = SlashCommandListener(jda,companyManager,historyManager,broadCast,bankManager,stockManager)

    jda.addEventListener(slashCommandListener)
    jda.addEventListener(ButtonListener(jda,companyManager,historyManager,slashCommandListener,bankManager,stockManager))
    jda.addEventListener(broadCast)

    // ステータス更新
    jda.presence.activity = Activity.playing("開発｜まぐシステム")

    //ステータス
    statusUpdater.start()

    val snapshotInterval = 60 * 1000L // 1分
    stockHistoryTimer = fixedRateTimer("stockHistoryTimer", daemon = true, initialDelay = 0, period = snapshotInterval) {
        historyManager.recordSnapshot()
    }

    fun scheduleSmallEvent() {
        val delay = Random.nextLong(60_000L, 180_000L) // 1分〜3分のランダム
        Timer(true).schedule(object : TimerTask() {
            override fun run() {
                companyManager.smallEvent()
                scheduleSmallEvent() // 次のイベントを再スケジュール
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
        bankManager,
        statusCh,
        pluginManager
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
    manager.startInteractive()

    statusCh.manager.setName("Status:稼働中").queue()
}
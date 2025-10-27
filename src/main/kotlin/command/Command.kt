@file:Suppress("unused")

package com.github.rei0925.magufinance.command

import com.github.rei0925.kotlincli.commands.*
import net.dv8tion.jda.api.entities.Activity
import com.github.rei0925.magufinance.*

@CommandAlias("reload")
class ReloadCommand(private val ctx: CommandContext) : BaseCommand() {
    @Default
    fun execute(args: List<String>) {
        ctx.jda.presence.activity = Activity.playing("MaguFinance｜再起動中")
        ctx.companyManager.reloadCompanies()
        ctx.statusUpdater.restart()
        println("情報を再読み込みしました")
    }
}

@CommandAlias("realtime")
class RealtimeCommand(private val ctx: CommandContext) : BaseCommand() {
    @Subcommand("start")
    fun start(args: List<String>) {
        RealTimeChart.start(ctx.companyManager, ctx.historyManager)
    }

    @Subcommand("stop")
    fun stop(args: List<String>) {
        RealTimeChart.stop()
    }
}

@CommandAlias("realtime4")
class Realtime4Command(private val ctx: CommandContext) : BaseCommand() {
    @Subcommand("start")
    fun start(args: List<String>) {
        RealTimeChart4.start(ctx.companyManager, ctx.historyManager, ctx.newsManager)
    }

    @Subcommand("stop")
    fun stop(args: List<String>) {
        RealTimeChart4.stop()
    }
}

@CommandAlias("ticker")
class TickerCommand(private val ctx: CommandContext) : BaseCommand() {
    @Subcommand("start")
    fun start(args: List<String>) {
        UnifiedTicker.start()
    }

    @Subcommand("start2")
    fun start2(args: List<String>) {
        RealTimeChart4.start(ctx.companyManager, ctx.historyManager, ctx.newsManager)
        UnifiedTicker.start()
    }

    @Subcommand("stop")
    fun stop(args: List<String>) {
        UnifiedTicker.stop()
    }
}

@CommandAlias("button")
class ButtonCommand(private val ctx: CommandContext) : BaseCommand() {
    @Default
    fun execute(args: List<String>) {
        print("チャンネルID: ")
        val idInput = ctx.reader.readLine()?.trim() ?: return
        val id = idInput.toLongOrNull()
        if (id == null) {
            println("数字を指定してください")
            return
        }
        ctx.broadCast.setButton(id)
    }
}

@CommandAlias("plugin")
class OnPlugin(private val ctx: CommandContext) : BaseCommand(){
    @Default
    fun plugin(){
        logger.info("Loaded plugins: ${ctx.pluginManager.plugins.joinToString(", ")}")
    }
}
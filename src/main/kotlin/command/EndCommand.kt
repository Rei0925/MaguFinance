package com.github.rei0925.command

import com.github.rei0925.RealTimeChart4
import com.github.rei0925.UnifiedTicker
import net.dv8tion.jda.api.entities.Activity
import kotlin.system.exitProcess

@CommandAlias("end")
class EndCommand(private val ctx: CommandContext) : BaseCommand() {
    fun execute(args: List<String>) {
        println("Bot を終了します...")

        ctx.jda.presence.activity = Activity.playing("MaguFinance｜システム終了中")

        try { ctx.statusUpdater.cancel() } catch (e: Exception) {}
        try { ctx.stockHistoryTimer.cancel() } catch (e: Exception) {}
        try {
            RealTimeChart4.stop()
            UnifiedTicker.stop()
            ctx.jda.shutdown()
            ctx.jda.awaitShutdown()
        } catch (e: Exception) {
            println("shutdown エラー: ${e.message}")
        }

        ctx.dbManager.disconnect()
        println("終了完了")
        exitProcess(0)
    }
}
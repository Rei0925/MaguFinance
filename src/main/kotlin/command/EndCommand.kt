package com.github.rei0925.command

import com.github.rei0925.RealTimeChart4
import com.github.rei0925.UnifiedTicker
import com.github.rei0925.kotlincli.commands.*
import net.dv8tion.jda.api.entities.Activity
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

@CommandAlias("end")
@com.github.rei0925.kotlincli.commands.EndCommand
class EndCommand(private val ctx: CommandContext) : BaseCommand() {
    private val logger = LoggerFactory.getLogger(EndCommand::class.java)
    @Default
    fun execute(args: List<String>) {
        logger.info("Bot を終了します...")

        ctx.jda.presence.activity = Activity.playing("MaguFinance｜システム終了中")
        ctx.statusChannel.manager.setName("Status:停止").queue()

        try { ctx.statusUpdater.stop() } catch (e: Exception) {}
        try { ctx.stockHistoryTimer.cancel() } catch (e: Exception) {}
        try {
            RealTimeChart4.stop()
            UnifiedTicker.stop()
            ctx.jda.shutdown()
            ctx.jda.awaitShutdown()
            ctx.pluginManager.unloadPlugins()
        } catch (e: Exception) {
            logger.error("shutdown エラー: ${e.message}")
        }

        ctx.dbManager.disconnect()
        logger.info("終了完了")
        exitProcess(0)
    }
}
package com.github.rei0925.command

import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import com.github.rei0925.kotlincli.commands.*
import com.github.rei0925.logger

@CommandAlias("maintenance")
class MaintenanceCommand(private val ctx: CommandContext) : BaseCommand() {

    @Subcommand("start")
    fun start(args: List<String>) {
        MaintenanceStatus.isMaintenance = true
        ctx.statusUpdater.stop()
        ctx.jda.presence.activity = Activity.playing("MaguFinance | メンテナンス中")
        ctx.statusChannel.manager.setName("Status:メンテナンス中").queue()
        ctx.jda.presence.isIdle = true
        ctx.jda.presence.setStatus(OnlineStatus.IDLE)
        logger.info("メンテナンスモードに移行しました")
    }


    @Subcommand("stop")
    fun stop(args: List<String>) {
        MaintenanceStatus.isMaintenance = false
        ctx.jda.presence.activity = Activity.playing("MaguFinance | 稼働中")
        ctx.jda.presence.setStatus(OnlineStatus.ONLINE)
        ctx.statusChannel.manager.setName("Status:稼働中").queue()
        logger.info("メンテナンスモードを解除しました")
        ctx.statusUpdater.start()
    }
}
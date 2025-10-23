package com.github.rei0925.command

import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity

@CommandAlias("maintenance")
class MaintenanceCommand(private val ctx: CommandContext) : BaseCommand() {

    @Subcommand("start")
    fun start(args: List<String>) {
        ctx.statusUpdater.cancel()
        ctx.stockHistoryTimer.cancel()
        ctx.jda.presence.activity = Activity.playing("MaguFinance | メンテナンス中")
        ctx.jda.presence.isIdle = true
        ctx.jda.presence.setStatus(OnlineStatus.IDLE)
    }

    @Subcommand("stop")
    fun stop(args: List<String>) {
        ctx.jda.presence.activity = Activity.playing("MaguFinance | 稼働中")
        ctx.jda.presence.setStatus(OnlineStatus.ONLINE)
    }
}
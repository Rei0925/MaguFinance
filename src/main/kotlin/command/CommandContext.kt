package com.github.rei0925.magufinance.command

import com.github.rei0925.magufinance.*
import com.github.rei0925.magufinance.manager.BankManager
import com.github.rei0925.magufinance.manager.CompanyManager
import com.github.rei0925.magufinance.manager.HistoryManager
import com.github.rei0925.magufinance.manager.MarketManager
import com.github.rei0925.magufinance.manager.NewsManager
import com.github.rei0925.magufinance.manager.PluginManager
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import java.io.BufferedReader
import java.util.Timer

data class CommandContext(
    val jda: JDA,
    val dbManager: DBManager,
    val reader: BufferedReader,
    val companyManager: CompanyManager,
    val marketManager: MarketManager,
    val historyManager: HistoryManager,
    val newsManager: NewsManager,
    val broadCast: BroadCast,
    val statusUpdater: StatusUpdater,
    val stockHistoryTimer: Timer,
    val bankManager: BankManager,
    val statusChannel: VoiceChannel,
    val pluginManager: PluginManager
)
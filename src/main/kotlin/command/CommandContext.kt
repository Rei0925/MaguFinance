package com.github.rei0925.command

import com.github.rei0925.*
import net.dv8tion.jda.api.JDA
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
    val statusUpdater: Timer,
    val stockHistoryTimer: Timer,
    val bankManager: BankManager
)
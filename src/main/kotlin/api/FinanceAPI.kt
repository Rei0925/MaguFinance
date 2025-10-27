package com.github.rei0925.api

import com.github.rei0925.manager.BankManager
import com.github.rei0925.manager.CompanyManager
import com.github.rei0925.DBManager
import com.github.rei0925.manager.HistoryManager
import com.github.rei0925.manager.MarketManager
import com.github.rei0925.manager.NewsManager
import net.dv8tion.jda.api.JDA

interface FinanceAPI {
    fun getJda(): JDA
    fun getDbManager(): DBManager
    fun getCompanyManager(): CompanyManager
    fun getMarketManager(): MarketManager
    fun getHistoryManager(): HistoryManager
    fun getNewsManager(): NewsManager
    fun getBankManager(): BankManager
}
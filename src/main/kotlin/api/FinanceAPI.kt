package com.github.rei0925.magufinance.api

import com.github.rei0925.magufinance.manager.BankManager
import com.github.rei0925.magufinance.manager.CompanyManager
import com.github.rei0925.magufinance.DBManager
import com.github.rei0925.magufinance.manager.HistoryManager
import com.github.rei0925.magufinance.manager.MarketManager
import com.github.rei0925.magufinance.manager.NewsManager
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
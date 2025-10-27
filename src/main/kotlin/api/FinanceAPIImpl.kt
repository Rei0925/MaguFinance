package com.github.rei0925.magufinance.api

import com.github.rei0925.magufinance.DBManager
import com.github.rei0925.magufinance.api.FinanceAPI
import com.github.rei0925.magufinance.manager.BankManager
import com.github.rei0925.magufinance.manager.CompanyManager
import com.github.rei0925.magufinance.manager.HistoryManager
import com.github.rei0925.magufinance.manager.MarketManager
import com.github.rei0925.magufinance.manager.NewsManager
import net.dv8tion.jda.api.JDA

class FinanceAPIImpl(
    private val jda: JDA,
    private val dbManager: DBManager,
    private val companyManager: CompanyManager,
    private val marketManager: MarketManager,
    private val historyManager: HistoryManager,
    private val newsManager: NewsManager,
    private val bankManager: BankManager
) : FinanceAPI {

    override fun getJda(): JDA = jda
    override fun getDbManager(): DBManager = dbManager
    override fun getCompanyManager(): CompanyManager = companyManager
    override fun getMarketManager(): MarketManager = marketManager
    override fun getHistoryManager(): HistoryManager = historyManager
    override fun getNewsManager(): NewsManager = newsManager
    override fun getBankManager(): BankManager = bankManager
}
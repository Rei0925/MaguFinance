package com.github.rei0925.magufinance.api

import com.github.rei0925.magufinance.DBManager
import com.github.rei0925.magufinance.manager.*
import net.dv8tion.jda.api.JDA

interface FinanceAPI {

    fun getJda(): JDA
    fun getDbManager(): DBManager
    fun getCompanyManager(): CompanyManager
    fun getMarketManager(): MarketManager
    fun getHistoryManager(): HistoryManager
    fun getNewsManager(): NewsManager
    fun getBankManager(): BankManager

    companion object {
        private var instance: FinanceAPI? = null

        fun getInstance(): FinanceAPI {
            return instance ?: throw IllegalStateException("FinanceAPI is not initialized")
        }

        fun setInstance(api: FinanceAPI) {
            instance = api
        }
    }
}
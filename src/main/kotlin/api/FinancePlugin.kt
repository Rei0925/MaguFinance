package com.github.rei0925.api

interface FinancePlugin {
    val name: String
    fun onEnable(api: FinanceAPI)
    fun onDisable()
}
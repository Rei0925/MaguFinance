package com.github.rei0925.command

@CommandAlias("co")
class CompanyCommand(private val ctx: CommandContext) : BaseCommand() {

    @Subcommand("create")
    fun create(args: List<String>) {
        print("会社名: ")
        val name = ctx.reader.readLine()?.trim() ?: return
        print("初期株価: ")
        val price = ctx.reader.readLine()?.toDoubleOrNull() ?: return
        print("発行株数: ")
        val total = ctx.reader.readLine()?.toIntOrNull() ?: return

        ctx.companyManager.createCompany(name, price, total)
    }
}
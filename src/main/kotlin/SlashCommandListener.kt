package com.github.rei0925.magufinance

import com.github.rei0925.magufinance.command.MaintenanceStatus
import com.github.rei0925.magufinance.manager.BankManager
import com.github.rei0925.magufinance.manager.CompanyManager
import com.github.rei0925.magufinance.manager.HistoryManager
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.utils.FileUpload
import org.knowm.xchart.BitmapEncoder
import org.knowm.xchart.BitmapEncoder.BitmapFormat
import org.knowm.xchart.XYChartBuilder
import org.knowm.xchart.style.markers.SeriesMarkers
import org.slf4j.LoggerFactory
import java.awt.Color
import java.io.File
import java.util.*

class SlashCommandListener(
    private val jda: JDA,
    private val companyManager: CompanyManager,
    private val historyManager: HistoryManager,
    private val broadCast: BroadCast,
    private val bankManager: BankManager,
    private val stockManager: StockManager
) : ListenerAdapter() {
    private val logger = LoggerFactory.getLogger("Command")

    val tempMessages = mutableMapOf<Long, Message>()
    fun commandLog(name: String,command: String){
        logger.info("$name issued discord command: $command")
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        commandLog(event.user.name,event.commandString)
        if (MaintenanceStatus.isMaintenance) {
            event.reply("現在メンテナンス中です。後ほどお試しください。")
                .setEphemeral(true)
                .queue()
            return
        }
        when (event.name) {
            "stock" -> {
                when (event.subcommandName){
                    "price" -> {
                        val prices = companyManager.companyList.joinToString("\n") { company ->
                            "${company.name}｜${"%.2f".format(company.stockPrice)}円"
                        }
                        event.reply("現在の株価一覧:\n$prices").queue()
                    }
                    "history" -> {
                        val companyName = event.getOption("company")?.asString
                        val history = historyManager.getHistory()

                        if (history.isEmpty()) {
                            event.reply("履歴がまだありません。").queue()
                            return
                        }

                        val filtered = if (companyName != null) {
                            history.filter { it.companyName.equals(companyName, ignoreCase = true) }
                        } else {
                            history
                        }

                        if (filtered.isEmpty()) {
                            event.reply("会社 `$companyName` の履歴は存在しません。").queue()
                            return
                        }


                        val chart = XYChartBuilder()
                            .width(700)
                            .height(400)
                            .title(
                                if (companyName != null) "株価履歴: $companyName"
                                else "株価履歴（複数社）"
                            )
                            .xAxisTitle("日時")
                            .yAxisTitle("株価")
                            .build()

                        if (companyName != null) {
                            val latest = filtered.takeLast(50)
                            val timestamps = latest.map { entry ->
                                Date(entry.timestamp)
                            }
                            val prices = latest.map { it.stockPrice }

                            val series = chart.addSeries(companyName, timestamps, prices)
                            series.marker = SeriesMarkers.CIRCLE
                        } else {
                            // Group by company name
                            val grouped = filtered.groupBy { it.companyName }
                            // Sort companies by number of recent entries descending, take top 5
                            val topCompanies = grouped.entries
                                .sortedByDescending { it.value.size }
                                .take(5)

                            for ((name, entries) in topCompanies) {
                                val latest = entries.takeLast(50)
                                val timestamps = latest.map { entry -> Date(entry.timestamp) }
                                val prices = latest.map { it.stockPrice }
                                val series = chart.addSeries(name, timestamps, prices)
                                series.marker = SeriesMarkers.CIRCLE
                            }
                            // Add domestic average series
                            val avgHistory = historyManager.getAverageHistory().takeLast(50)
                            if (avgHistory.isNotEmpty()) {
                                val avgTimestamps = avgHistory.map { Date(it.timestamp) }
                                val avgPrices = avgHistory.map { it.averagePrice }
                                val avgSeries = chart.addSeries("国内平均", avgTimestamps, avgPrices)
                                avgSeries.marker = SeriesMarkers.DIAMOND
                            }
                        }

                        // Save to temp file
                        val file = File.createTempFile("stock-history-", ".png")
                        BitmapEncoder.saveBitmap(chart, file.absolutePath, BitmapFormat.PNG)

                        event.replyFiles(FileUpload.fromData(file)).setEphemeral(true).queue {
                            file.delete()
                        }
                    }

                    "show" -> {
                        val userId = event.user.idLong
                        val ownedStocks = stockManager.getOwnedStocks(userId)
                            .filter { it.amount > 0 } // 所持株のみ

                        if (ownedStocks.isEmpty()) {
                            val embed = EmbedBuilder()
                                .setTitle("所有株なし")
                                .setDescription("現在、所有している株はありません。")
                                .setColor(Color.YELLOW)
                                .setFooter("MaguFinance")
                                .build()
                            event.replyEmbeds(embed).setEphemeral(true).queue()
                            return
                        }

                        val embed = EmbedBuilder()
                            .setTitle("${event.user.name} の保有株")
                            .setColor(Color.GREEN)
                            .setFooter("MaguFinance")

                        ownedStocks.forEach { stock ->
                            val company = companyManager.getCompanyById(stock.companyId)
                            if (company != null) {
                                embed.addField(
                                    company.name,
                                    "株数: ${stock.amount}｜現在の価格: ¥${company.stockPrice}｜評価額: ¥${"%.2f".format(stock.amount * company.stockPrice)}",
                                    false
                                )
                            }
                        }

                        event.replyEmbeds(embed.build()).setEphemeral(true).queue()
                    }

                    "buy" -> {
                        val companyName = event.getOption("company")?.asString ?: return
                        val pieces = event.getOption("pieces")?.asInt ?: return
                        val company = companyManager.getCompany(companyName)

                        if (company == null) {
                            val embed = EmbedBuilder()
                                .setTitle("エラー")
                                .setDescription("会社が存在しません。")
                                .setColor(Color.RED)
                                .setFooter("MaguFinance")
                                .build()
                            event.replyEmbeds(embed).setEphemeral(true).queue()
                            return
                        }

                        val totalPrice = company.stockPrice * pieces
                        val userBalance = bankManager.getBalance(event.user.idLong)
                        if (userBalance < totalPrice) {
                            val embed = EmbedBuilder()
                                .setTitle("購入不可")
                                .setDescription("残高が不足しています。必要金額: ¥$totalPrice, 残高: ¥$userBalance")
                                .setColor(Color.RED)
                                .setFooter("MaguFinance")
                                .build()
                            event.replyEmbeds(embed).setEphemeral(true).queue()
                            return
                        }

                        val embed = EmbedBuilder()
                            .setTitle("購入確認")
                            .setDescription("会社: ${company.name}\n株数: $pieces\n合計: ¥$totalPrice\n本当に購入しますか？")
                            .setColor(Color.YELLOW)
                            .setFooter("MaguFinance")
                            .build()

                        val buyButton = Button.success("buy_confirm:${event.user.id}:${company.id}:$pieces", "購入する")
                        val cancelButton = Button.danger("buy_cancel:${event.user.id}", "キャンセル")

                        event.replyEmbeds(embed)
                            .addComponents(ActionRow.of(buyButton, cancelButton))
                            .setEphemeral(true)
                            .queue { hook ->
                                hook.retrieveOriginal().queue { message ->
                                    tempMessages[event.user.idLong] = message
                                }
                            }
                    }
                    "sell" -> {
                        val companyName = event.getOption("company")?.asString ?: return
                        val pieces = event.getOption("pieces")?.asInt ?: return
                        val userId = event.user.idLong
                        val company = companyManager.getCompany(companyName)
                        val ownedStock = stockManager.getOwnedStocks(userId)
                            .find { it.companyId == company?.id }

                        if (company == null) {
                            val embed = EmbedBuilder()
                                .setTitle("エラー")
                                .setDescription("会社が存在しません。")
                                .setColor(Color.RED)
                                .setFooter("MaguFinance")
                                .build()
                            event.replyEmbeds(embed).setEphemeral(true).queue()
                            return
                        }

                        if (ownedStock == null || ownedStock.amount <= 0) {
                            val embed = EmbedBuilder()
                                .setTitle("エラー")
                                .setDescription("あなたはこの会社の株を所有していません。")
                                .setColor(Color.RED)
                                .setFooter("MaguFinance")
                                .build()
                            event.replyEmbeds(embed).setEphemeral(true).queue()
                            return
                        }

                        if (pieces > ownedStock.amount) {
                            val embed = EmbedBuilder()
                                .setTitle("エラー")
                                .setDescription("指定した株数は保有株数を超えています。")
                                .setColor(Color.RED)
                                .setFooter("MaguFinance")
                                .build()
                            event.replyEmbeds(embed).setEphemeral(true).queue()
                            return
                        }

                        if (pieces <= 0) {
                            val embed = EmbedBuilder()
                                .setTitle("エラー")
                                .setDescription("売却株数は正の整数で指定してください。")
                                .setColor(Color.RED)
                                .setFooter("MaguFinance")
                                .build()
                            event.replyEmbeds(embed).setEphemeral(true).queue()
                            return
                        }

                        val totalSellPrice = company.stockPrice * pieces
                        val currentMoney = bankManager.getBalance(userId)
                        val afterMoney = currentMoney + totalSellPrice
                        val afterPieces = ownedStock.amount - pieces

                        val embed = EmbedBuilder()
                            .setTitle("売却確認")
                            .setDescription("本当に売却しますか？")
                            .addField("現在の株数", "${ownedStock.amount}", true)
                            .addField("売却後の株数", "$afterPieces", true)
                            .addField("現在の所持金", "¥$currentMoney", true)
                            .addField("売却後の所持金", "¥$afterMoney", true)
                            .setColor(Color.YELLOW)
                            .setFooter("MaguFinance")
                            .build()

                        val sellButton = Button.success("sell_confirm:${event.user.id}:${company.id}:$pieces", "売却する")
                        val cancelButton = Button.danger("sell_cancel:${event.user.id}", "キャンセル")

                        event.replyEmbeds(embed)
                            .addComponents(ActionRow.of(sellButton, cancelButton))
                            .setEphemeral(true)
                            .queue { hook ->
                                hook.retrieveOriginal().queue { message ->
                                    tempMessages[event.user.idLong] = message
                                }
                            }
                    }
                }
            }

            "broad-cast" -> {
                val ch = event.getOption("channel")?.asChannel?.let {
                    jda.getTextChannelById(it.id) ?: jda.getNewsChannelById(it.id)
                }

                if (ch == null){
                    event.reply("エラーが発生しました。\nそのチャンネルは存在しません").queue()
                    return
                }

                if (ch.type == ChannelType.NEWS) {
                    val embed = EmbedBuilder()
                        .setTitle("エラーが発生しました")
                        .setDescription("スパム対策の観点からアナウンスチャンネルは設定できません。")
                        .setColor(Color.RED)
                        .setFooter("MaguFinance")
                        .build()
                    event.replyEmbeds(embed).setEphemeral(true).queue()
                    return
                }

                val embed = EmbedBuilder()
                    .setTitle("お知らせチャンネルに設定されました")
                    .setDescription("MaguFinanceのお知らせ情報がこれからここに送信されます。")
                    .setColor(Color.GREEN)
                    .setFooter("MaguFinance")
                    .build()

                event.reply("お知らせチャンネルを${ch.jumpUrl}に設定しました").queue()
                broadCast.registerBroadcastChannel(ch.guild.idLong,ch.idLong)
                ch.sendMessageEmbeds(embed).queue()
            }

            "atm" -> {
                when (event.subcommandName){
                    "balance" ->{
                        val user = event.user
                        val bal = bankManager.getBalance(user.idLong)
                        if (bal == -1.0){
                            val embed = EmbedBuilder()
                                .setTitle("口座が存在しません")
                                .setDescription("新規口座を開設しますか？")
                                .setColor(Color.RED)
                                .setFooter("MaguFinance")
                                .build()

                            val doneButton: Button = Button.success("ac_done", "開設")
                            val cancelButton = Button.danger("ac_cancel", "キャンセル")
                            event.replyEmbeds(embed)
                                .addComponents(ActionRow.of(doneButton, cancelButton))
                                .setEphemeral(true)
                                .queue { hook ->
                                    hook.retrieveOriginal().queue { message ->
                                        tempMessages[event.user.idLong] = message
                                    }
                                }

                            return
                        }
                        val embed = EmbedBuilder()
                            .setTitle("口座情報")
                            .addField("ID",event.user.idLong.toString(),false)
                            .addField("残高","¥$bal",false)
                            .setColor(Color.GREEN)
                            .setTimestamp(java.time.OffsetDateTime.now())
                            .setFooter("MaguFinance")
                            .build()

                        event.replyEmbeds(embed).setEphemeral(true).queue()
                    }
                }
            }
        }
    }


    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (event.modalId == "report-modal") {
            val type = event.getValue("type")?.asString ?: ""
            val target = event.getValue("target")?.asString ?: ""
            val reason = event.getValue("reason")?.asString ?: ""
            val from = event.getValue("from")?.asString ?: ""
            val sender = event.user.name
            // Assuming ReportSystem.report(type, sender, target, reason) exists
            ReportSystem.report(type, sender, target, reason, from)
            event.reply("通報を受け付けました。ご協力ありがとうございます。通報Idは要望があれば内閣から伝達します。").setEphemeral(true).queue()
        }
    }

    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
        when (event.name) {
            "stock" -> {
                when (event.subcommandName) {
                    "buy" -> {
                        if (event.focusedOption.name == "company") {
                            val input = event.focusedOption.value.lowercase()
                            val allCompanies = companyManager.getCompanies()
                                .map { it.name }
                                .filter { it.lowercase().startsWith(input) }
                                .take(25)
                                .map { Command.Choice(it, it) }
                            event.replyChoices(allCompanies).queue()
                        }
                    }
                    "sell" -> {
                        if (event.focusedOption.name == "company") {
                            val userId = event.user.idLong
                            val ownedCompanies = stockManager.getOwnedStocks(userId)
                                .filter { it.amount > 0 }           // 所持株のみ
                                .map { companyManager.getCompanyById(it.companyId)?.name ?: "" }

                            val input = event.focusedOption.value.lowercase()
                            val suggestions = ownedCompanies
                                .filter { it.lowercase().startsWith(input) } // 入力にマッチするものだけ
                                .take(25)                                    // 最大25件
                                .map { Command.Choice(it, it) }

                            event.replyChoices(suggestions).queue()
                        }
                    }
                    "history" -> {
                        if (event.focusedOption.name == "company") {
                            val input = event.focusedOption.value.lowercase()
                            val allCompanies = companyManager.getCompanies()
                                .map { it.name }
                                .filter { it.lowercase().startsWith(input) } // 入力にマッチするものだけ
                                .take(25)
                                .map { Command.Choice(it, it) }

                            event.replyChoices(allCompanies).queue()
                        }
                    }
                }
            }
        }
    }
}
package com.github.rei0925

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import net.dv8tion.jda.api.utils.FileUpload
import org.knowm.xchart.BitmapEncoder
import org.knowm.xchart.BitmapEncoder.BitmapFormat
import org.knowm.xchart.XYChartBuilder
import org.knowm.xchart.style.markers.SeriesMarkers
import java.io.File
import java.util.*

class SlashCommandListener : ListenerAdapter() {
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        when (event.name) {
            "stok-price" -> {
                val prices = CompanyManager.companyList.joinToString("\n") { company ->
                    "${company.name}｜${"%.2f".format(company.stockPrice)}円"
                }
                event.reply("現在の株価一覧:\n$prices").queue()
            }

            "report" -> {
                val typeInput = TextInput.create("type", "種類", TextInputStyle.SHORT)
                    .setPlaceholder("Chat, Cheat(荒し)などから選んでください")
                    .setRequired(true)
                    .build()
                val targetInput = TextInput.create("target", "対象", TextInputStyle.SHORT)
                    .setPlaceholder("MessageID または相手のユーザー名を入力してください")
                    .setRequired(true)
                    .build()
                val reasonInput = TextInput.create("reason", "理由", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("理由")
                    .setRequired(true)
                    .build()
                val fromInput = TextInput.create("from", "場所", TextInputStyle.SHORT)
                    .setPlaceholder("DiscordまたはMinecraft")
                    .setRequired(true)
                    .build()
                val modal = Modal.create("report-modal", "通報フォーム")
                    .addActionRow(typeInput)
                    .addActionRow(targetInput)
                    .addActionRow(reasonInput)
                    .addActionRow(fromInput)
                    .build()
                event.replyModal(modal).queue()
            }

            "stok-history" -> {
                val companyName = event.getOption("company")?.asString
                val history = HistoryManager.getHistory()

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
                    val avgHistory = HistoryManager.getAverageHistory().takeLast(50)
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

                event.replyFiles(FileUpload.fromData(file)).queue {
                    file.delete()
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
}
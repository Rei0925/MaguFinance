package com.github.rei0925

import com.github.rei0925.command.MaintenanceStatus
import com.github.rei0925.manager.BankManager
import com.github.rei0925.manager.CompanyManager
import com.github.rei0925.manager.HistoryManager
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.awt.Color

class ButtonListener(
    private val jda: JDA,
    private val companyManager: CompanyManager,
    private val historyManager: HistoryManager,
    private val slashCommandListener: SlashCommandListener,
    private val bankManager: BankManager,
    private val stockManager: StockManager
) : ListenerAdapter() {

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        if (MaintenanceStatus.isMaintenance) {
            event.reply("現在メンテナンス中です。後ほどお試しください。")
                .setEphemeral(true)
                .queue()
            return
        }
        val idParts = event.componentId.split(":")
        when (idParts[0]) {
            "ac_done" -> {
                val user = event.user
                bankManager.createAccount(user.idLong)
                val embed = EmbedBuilder()
                    .setTitle("新規口座を開設しました")
                    .addField("口座ID", user.id, true)
                    .addField("所持金", "¥${com.github.rei0925.bankManager.getBalance(user.idLong)}" , true)
                    .setColor(Color.BLUE)
                    .setFooter("MaguFinance")
                    .setTimestamp(java.time.OffsetDateTime.now())
                    .build()

                // 元のメッセージを編集して埋め込みに置き換え、ボタンは消す
                val originalMessage = slashCommandListener.tempMessages[user.idLong] ?: return
                originalMessage.editMessageEmbeds(embed)
                    .setComponents()
                    .queue()

                slashCommandListener.tempMessages.remove(user.idLong)
            }
            "ac_cancel" -> {
                val user = event.user
                val embed = EmbedBuilder()
                    .setTitle("キャンセルしました")
                    .setColor(Color.RED)
                    .setFooter("MaguFinance")
                    .setTimestamp(java.time.OffsetDateTime.now())
                    .build()
                // 元のメッセージを編集して埋め込みに置き換え、ボタンは消す
                val originalMessage = slashCommandListener.tempMessages[user.idLong] ?: return
                originalMessage.editMessageEmbeds(embed)
                    .setComponents()
                    .queue()

                slashCommandListener.tempMessages.remove(user.idLong)
            }
            "buy_confirm" -> {
                val userId = idParts[1].toLong()
                val companyId = idParts[2].toInt()
                val pieces = idParts[3].toInt()

                val company = companyManager.getCompanyById(companyId)
                val balance = bankManager.getBalance(userId)

                if (company == null || balance < company.stockPrice * pieces) {
                    val embed = EmbedBuilder()
                        .setTitle("購入失敗")
                        .setDescription("残高不足または会社が存在しません。")
                        .setColor(Color.RED)
                        .setFooter("MaguFinance")
                        .build()
                    slashCommandListener.tempMessages[userId]?.editMessageEmbeds(embed)?.setComponents()?.queue()
                    slashCommandListener.tempMessages.remove(userId)
                    return
                }

                // 購入処理
                stockManager.buy(userId, companyId, pieces)
                companyManager.buyStock(company.name, pieces)
                bankManager.removeBalance(userId, company.stockPrice * pieces)

                val embed = EmbedBuilder()
                    .setTitle("購入完了")
                    .addField("会社",company.name,true)
                    .addField("株数",pieces.toString(),true)
                    .addField("合計","¥${company.stockPrice * pieces}",true)
                    .addField("購入後残高","¥${bankManager.getBalance(userId)}",true)
                    .setColor(Color.GREEN)
                    .setFooter("MaguFinance")
                    .build()

                val embedR = EmbedBuilder()
                    .setTitle("領収書")
                    .setDescription("購入が完了しましたので以下の通りお知らせいたします。")
                    .addField("会社",company.name,true)
                    .addField("株数",pieces.toString(),true)
                    .addField("合計","¥${company.stockPrice * pieces}",true)
                    .addField("購入後残高","¥${bankManager.getBalance(userId)}",true)
                    .setDescription("${event.user.globalName}の残高から${company.stockPrice * pieces}円口座振替を行いました。")
                    .setColor(Color.GREEN)
                    .setFooter("MaguFinance")
                    .build()

                slashCommandListener.tempMessages[userId]?.editMessageEmbeds(embed)?.setComponents()?.queue()
                event.user.openPrivateChannel().queue { channel ->
                    channel.sendMessageEmbeds(embedR).queue()
                }
                slashCommandListener.tempMessages.remove(userId)
            }
            "buy_cancel" -> {
                val userId = idParts[1].toLong()
                val embed = EmbedBuilder()
                    .setTitle("購入キャンセル")
                    .setColor(Color.RED)
                    .setFooter("MaguFinance")
                    .build()
                slashCommandListener.tempMessages[userId]?.editMessageEmbeds(embed)?.setComponents()?.queue()
                slashCommandListener.tempMessages.remove(userId)
            }
            "sell_confirm" -> {
                if (idParts.size < 4) return
                val userId = idParts[1].toLongOrNull() ?: return
                val companyId = idParts[2].toIntOrNull() ?: return
                val pieces = idParts[3].toIntOrNull() ?: return

                if (pieces <= 0) {
                    slashCommandListener.tempMessages[userId]?.editMessageEmbeds(
                        EmbedBuilder()
                            .setTitle("売却失敗")
                            .setDescription("売却株数は正の整数で指定してください。")
                            .setColor(Color.RED)
                            .setFooter("MaguFinance")
                            .build()
                    )?.setComponents()?.queue()
                    slashCommandListener.tempMessages.remove(userId)
                    return
                }

                val company = companyManager.getCompanyById(companyId)
                val ownedStock = stockManager.getOwnedStocks(userId).find { it.companyId == companyId }
                val currentMoney = bankManager.getBalance(userId)

                if (company == null || ownedStock == null || ownedStock.amount < pieces) {
                    slashCommandListener.tempMessages[userId]?.editMessageEmbeds(
                        EmbedBuilder()
                            .setTitle("売却失敗")
                            .setDescription("株の保有数不足または会社が存在しません。")
                            .setColor(Color.RED)
                            .setFooter("MaguFinance")
                            .build()
                    )?.setComponents()?.queue()
                    slashCommandListener.tempMessages.remove(userId)
                    return
                }

                // 売却処理
                bankManager.addBalance(userId, company.stockPrice * pieces)
                stockManager.sell(userId, companyId, pieces)
                companyManager.sellStock(company.name, pieces)

                val embed = EmbedBuilder()
                    .setTitle("売却完了")
                    .addField("会社", company.name, true)
                    .addField("売却株数", "$pieces", true)
                    .addField("売却前の株数", "${ownedStock.amount}", true)
                    .addField("売却後の株数", "${ownedStock.amount - pieces}", true)
                    .addField("売却前の所持金", "¥$currentMoney", true)
                    .addField("売却後の所持金", "¥${currentMoney + company.stockPrice * pieces}", true)
                    .setColor(Color.GREEN)
                    .setFooter("MaguFinance")
                    .build()

                val embedR = EmbedBuilder()
                    .setTitle("領収書")
                    .setDescription("売却が完了したため以下の通りお知らせいたします。")
                    .addField("会社", company.name, true)
                    .addField("売却株数", "$pieces", true)
                    .addField("売却前の株数", "${ownedStock.amount}", true)
                    .addField("売却後の株数", "${ownedStock.amount - pieces}", true)
                    .addField("売却前の所持金", "¥$currentMoney", true)
                    .addField("売却後の所持金", "¥${currentMoney + company.stockPrice * pieces}", true)
                    .setDescription("${event.user.globalName}の口座に${company.stockPrice * pieces}円入金を行いました。")
                    .setColor(Color.GREEN)
                    .setFooter("MaguFinance")
                    .build()

                slashCommandListener.tempMessages[userId]?.editMessageEmbeds(embed)?.setComponents()?.queue()
                event.user.openPrivateChannel().queue { channel ->
                    channel.sendMessageEmbeds(embedR).queue()
                }
                slashCommandListener.tempMessages.remove(userId)
            }
            "sell_cancel" -> {
                val userId = idParts[1].toLong()
                val embed = EmbedBuilder()
                    .setTitle("売却キャンセル")
                    .setColor(Color.RED)
                    .setFooter("MaguFinance")
                    .build()
                slashCommandListener.tempMessages[userId]?.editMessageEmbeds(embed)?.setComponents()?.queue()
                slashCommandListener.tempMessages.remove(userId)
            }
        }
    }
}
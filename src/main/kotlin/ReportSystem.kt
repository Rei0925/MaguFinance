package com.github.rei0925

import net.dv8tion.jda.api.JDA

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.time.OffsetDateTime
import java.time.ZoneOffset
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import java.awt.Color
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

@Serializable
data class ReportEntry(
    val id: Int,
    val type: String,
    val sender: String,
    val target: String,
    val reason: String,
    val from: String,
    val status: String,
    val messageId: String? = null
)

object ReportSystem {
    private lateinit var modeFile: Path
    private var counter: Int = 1
    private lateinit var jda: JDA
    lateinit var reportChannelId: String

    fun initialize(dataDirectory: Path) {
        this.modeFile = dataDirectory.resolve("reports.jsonl")
        if (Files.exists(modeFile)) {
            val lines = Files.readAllLines(modeFile)
            if (lines.isNotEmpty()) {
                val lastLine = lines.last()
                try {
                    val idRegex = """"id"\s*:\s*(\d+)""".toRegex()
                    val matchResult = idRegex.find(lastLine)
                    if (matchResult != null) {
                        val lastId = matchResult.groupValues[1].toInt()
                        counter = lastId + 1
                    } else {
                        counter = 1
                    }
                } catch (_: Exception) {
                    counter = 1
                }
            } else {
                counter = 1
            }
        } else {
            counter = 1
        }
    }
    fun setup(jda: JDA, channelId: String) {
        this.jda = jda
        this.reportChannelId = channelId
    }

    fun report(type: String, sender:String, target: String, reason:String, from:String){
        /**
         * type     -> Chat や Cheat
         * sender   -> 送信者
         * target   -> ChatだったらMessageID,Cheatだったら対象者
         * reason   -> 理由
         * from     -> 場所(Discord or Minecraft)
         */
        if (this::jda.isInitialized && this::reportChannelId.isInitialized) {
            val channel = jda.getTextChannelById(1409518811274809394)
            channel?.sendMessage("<@&1385726863275720854>新規通報が作成されました")?.queue {
                val embed = EmbedBuilder()
                    .setTitle("新規通報情報")
                    .addField("Type", type, false)
                    .addField("Target", target, false)
                    .addField("Reason", reason, false)
                    .addField("From", from, false)
                    .addField("Id",counter.toString(),false)
                if (from == "Discord") {
                    try {
                        val userId = target.toLongOrNull()
                        if (userId != null) {
                            val user = jda.retrieveUserById(userId).complete()
                            embed.setThumbnail(user.effectiveAvatarUrl)
                        }
                    } catch (_: Exception) {
                        // ユーザー取得失敗時は何もしない
                    }
                } else if (from == "Minecraft") {
                    embed.setThumbnail("https://crafthead.net/helm/$target/300")
                }
                embed.setTimestamp(OffsetDateTime.now(ZoneOffset.UTC))
                embed.setFooter("Sender: $sender")
                val doneButton = Button.success("report_done_$counter", "対応完了")
                val cancelButton = Button.danger("report_cancel_$counter", "対応中止")
                channel.sendMessageEmbeds(embed.build())
                    .setActionRow(doneButton, cancelButton)
                    .queue { message ->
                        val entry = ReportEntry(
                            id = counter,
                            type = type,
                            sender = sender,
                            target = target,
                            reason = reason,
                            from = from,
                            status = "OPEN",
                            messageId = message.id
                        )
                        val json = Json.encodeToString(entry)
                        Files.write(modeFile, (json + "\n").toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                        counter++
                    }
            }
        }
    }
}

class ReportButton() : ListenerAdapter() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var reportFile: File

    fun initialize(pluginDataFolder: File) {
        reportFile = pluginDataFolder.resolve("reports.jsonl")
        if (!reportFile.exists()) {
            reportFile.parentFile.mkdirs()
            reportFile.createNewFile()
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val buttonId = event.button.id ?: return
        val (prefix, newStatus, replyMessage, embedTitle) = when {
            buttonId.startsWith("report_done_") -> Quad("report_done_", "DONE", "通報 %s の対応完了", "対応完了")
            buttonId.startsWith("report_cancel_") -> Quad("report_cancel_", "CANCELED", "通報 %s の対応を中止しました", "対応中止")
            else -> return
        }
        val reportIdStr = buttonId.removePrefix(prefix)

        // Show modal based on button type
        val modal = when (prefix) {
            "report_done_" -> {
                val input = TextInput.create("handling_content", "対応内容", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("対応内容を入力してください")
                    .setRequired(true)
                    .build()
                Modal.create("modal_done_$reportIdStr", "対応内容を入力してください")
                    .addActionRows(net.dv8tion.jda.api.interactions.components.ActionRow.of(input))
                    .build()
            }
            "report_cancel_" -> {
                val input = TextInput.create("cancel_reason", "中止理由", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("中止理由を入力してください")
                    .setRequired(true)
                    .build()
                Modal.create("modal_cancel_$reportIdStr", "中止理由を入力してください")
                    .addActionRows(net.dv8tion.jda.api.interactions.components.ActionRow.of(input))
                    .build()
            }
            else -> null
        }
        if (modal != null) {
            event.replyModal(modal).queue()
        }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        val modalId = event.modalId
        val (prefix, newStatus) = when {
            modalId.startsWith("modal_done_") -> "modal_done_" to "DONE"
            modalId.startsWith("modal_cancel_") -> "modal_cancel_" to "CANCELED"
            else -> return
        }
        val reportIdStr = modalId.removePrefix(prefix)
        val reportId = reportIdStr.toIntOrNull()
        if (reportId == null) {
            event.reply("無効な通報IDです").setEphemeral(true).queue()
            return
        }

        val inputContent = when (newStatus) {
            "DONE" -> event.getValue("handling_content")?.asString
            "CANCELED" -> event.getValue("cancel_reason")?.asString
            else -> null
        }
        if (inputContent.isNullOrBlank()) {
            event.reply("入力内容が空です。").setEphemeral(true).queue()
            return
        }

        // Read all reports and find the matching one
        val lines = if (::reportFile.isInitialized && reportFile.exists()) reportFile.readLines() else emptyList()
        var targetReport: JsonObject? = null

        for (line in lines) {
            val jsonObj = try { json.parseToJsonElement(line).jsonObject } catch (_: Exception) { null }
            val id = jsonObj?.get("id")?.jsonPrimitive?.intOrNull
            if (id == reportId) {
                targetReport = jsonObj
                break
            }
        }

        if (targetReport == null) {
            event.reply("通報 $reportId が見つかりませんでした").setEphemeral(true).queue()
            return
        }

        // Update status, handledBy, and add handlingContent or cancelReason
        val updatedReport = buildJsonObject {
            targetReport.forEach { (k, v) -> put(k, v) }
            put("status", JsonPrimitive(newStatus))
            put("handledBy", JsonPrimitive(event.user.name))
            when (newStatus) {
                "DONE" -> put("handlingContent", JsonPrimitive(inputContent))
                "CANCELED" -> put("cancelReason", JsonPrimitive(inputContent))
            }
        }
        val updatedLine = json.encodeToString(JsonObject.serializer(), updatedReport)
        // Overwrite the relevant line in the file
        val updatedLines = lines.map { line ->
            val jsonObj = try { json.parseToJsonElement(line).jsonObject } catch (_: Exception) { null }
            val id = jsonObj?.get("id")?.jsonPrimitive?.intOrNull
            if (id == reportId) updatedLine else line
        }
        reportFile.writeText(updatedLines.joinToString("\n") + if (updatedLines.isNotEmpty()) "\n" else "")

        // Get messageId and fetch message
        val messageId = updatedReport["messageId"]?.jsonPrimitive?.contentOrNull
        if (messageId == null) {
            event.reply("通報 $reportId のメッセージIDが見つかりませんでした").setEphemeral(true).queue()
            return
        }
        val channel = event.channel
        channel.retrieveMessageById(messageId).queue({ message ->
            val oldEmbed = message.embeds.firstOrNull()
            val embedBuilder = if (oldEmbed != null) EmbedBuilder(oldEmbed) else EmbedBuilder()
            val embedTitle = when (newStatus) {
                "DONE" -> "対応完了"
                "CANCELED" -> "対応中止"
                else -> ""
            }
            embedBuilder.setTitle(embedTitle)
            // Set color based on status
            when (newStatus) {
                "DONE" -> embedBuilder.setColor(Color.GREEN)
                "CANCELED" -> embedBuilder.setColor(Color.RED)
            }
            embedBuilder.addField("対応者", event.user.name, false)
            when (newStatus) {
                "DONE" -> embedBuilder.addField("対応内容", inputContent, false)
                "CANCELED" -> embedBuilder.addField("中止理由", inputContent, false)
            }
            message.editMessageEmbeds(embedBuilder.build()).queue {
                // After editing the original embed, send the new summary embed
                val summaryEmbed = EmbedBuilder()
                val summaryTitle = when (newStatus) {
                    "DONE" -> "対応 完了しました"
                    "CANCELED" -> "対応 中止しました"
                    else -> ""
                }
                val description = when (newStatus) {
                    "DONE" -> "通報Id$reportId でご報告いただいた件について対応が完了しました。"
                    "CANCELED" -> "通報Id$reportId でご報告いただいた件について対応が中止されました。"
                    else -> ""
                }
                val color = when (newStatus) {
                    "DONE" -> Color.GREEN
                    "CANCELED" -> Color.RED
                    else -> null
                }
                summaryEmbed.setTitle(summaryTitle)
                summaryEmbed.setDescription(description)
                summaryEmbed.addField("内容", inputContent, false)
                summaryEmbed.setFooter("以上の通りでこの報告の対応を終了いたしました。")
                color?.let { summaryEmbed.setColor(it) }
                channel.sendMessageEmbeds(summaryEmbed.build()).queue()
            }
        }, {
            event.reply("通報 $reportId のメッセージを取得できませんでした").setEphemeral(true).queue()
            return@queue
        })

        // Disable all buttons in the message's action rows instead of removing them
        val message = event.message
        val disabledActionRows = message?.actionRows?.map { row ->
            val disabledButtons = row.components.mapNotNull { comp ->
                if (comp is net.dv8tion.jda.api.interactions.components.buttons.Button) comp.asDisabled() else null
            }
            net.dv8tion.jda.api.interactions.components.ActionRow.of(disabledButtons)
        }

        disabledActionRows?.let { message.editMessageComponents(it) }?.queue()

        val replyMessage = when (newStatus) {
            "DONE" -> "通報 %s の対応完了"
            "CANCELED" -> "通報 %s の対応を中止しました"
            else -> ""
        }
        event.reply(String.format(replyMessage, reportId)).setEphemeral(true).queue()
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
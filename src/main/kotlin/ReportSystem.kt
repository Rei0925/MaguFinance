package com.github.rei0925.magufinance

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.OffsetDateTime
import java.time.ZoneOffset

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
            val channel = jda.getTextChannelById(1409761887364710500)
            channel?.sendMessage("新規通報が作成されました")?.queue {
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
                val doneButton: Button = Button.success("report_done_$counter", "対応完了")
                val cancelButton = Button.danger("report_cancel_$counter", "対応中止")
                channel.sendMessageEmbeds(embed.build())
                    .setComponents(ActionRow.of(doneButton, cancelButton))
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

class ReportButton : ListenerAdapter() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var reportFile: File

    fun initialize(pluginDataFolder: File) {
        reportFile = pluginDataFolder.resolve("reports.jsonl")
        if (!reportFile.exists()) {
            reportFile.parentFile.mkdirs()
            reportFile.createNewFile()
        }
    }
    /**
    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val buttonId = event.componentId
        val (prefix, newStatus) = when {
            buttonId.startsWith("report_done_") -> "report_done_" to "DONE"
            buttonId.startsWith("report_cancel_") -> "report_cancel_" to "CANCELED"
            else -> return
        }
        val reportIdStr = buttonId.removePrefix(prefix)

        val modal = when (prefix) {
            "report_done_" -> {
                val input : TextInput.Builder = TextInput.create("handling_content", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("対応内容を入力してください")
                    .setRequired(true)
                Modal.create("modal_done_$reportIdStr", "対応内容を入力してください")
                    .addComponents(input)
            }
            "report_cancel_" -> {
                val input = TextInput.create("cancel_reason",  TextInputStyle.PARAGRAPH)
                    .setPlaceholder("中止理由を入力してください")
                    .setRequired(true)
                Modal.create("modal_cancel_$reportIdStr", "中止理由を入力してください")
                    .addComponents(input)
            }
            else -> null
        }
        modal?.let { event.replyModal(it).queue() }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        val modalId = event.modalId
        val (prefix, newStatus) = when {
            modalId.startsWith("modal_done_") -> "modal_done_" to "DONE"
            modalId.startsWith("modal_cancel_") -> "modal_cancel_" to "CANCELED"
            else -> return
        }
        val reportIdStr = modalId.removePrefix(prefix)
        val reportId = reportIdStr.toIntOrNull() ?: run {
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

        val lines = if (::reportFile.isInitialized && reportFile.exists()) reportFile.readLines() else emptyList()
        val targetReport = lines.mapNotNull { line ->
            try { json.parseToJsonElement(line).jsonObject } catch (_: Exception) { null }
        }.firstOrNull { it["id"]?.jsonPrimitive?.intOrNull == reportId }

        if (targetReport == null) {
            event.reply("通報 $reportId が見つかりませんでした").setEphemeral(true).queue()
            return
        }

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
        val updatedLines = lines.map { line ->
            val jsonObj = try { json.parseToJsonElement(line).jsonObject } catch (_: Exception) { null }
            if (jsonObj?.get("id")?.jsonPrimitive?.intOrNull == reportId) updatedLine else line
        }
        reportFile.writeText(updatedLines.joinToString("\n") + if (updatedLines.isNotEmpty()) "\n" else "")

        val messageId = updatedReport["messageId"]?.jsonPrimitive?.contentOrNull
        if (messageId == null) {
            event.reply("通報 $reportId のメッセージIDが見つかりませんでした").setEphemeral(true).queue()
            return
        }

        event.channel.retrieveMessageById(messageId).queue({ message ->
            val oldEmbed = message.embeds.firstOrNull()
            val embedBuilder = EmbedBuilder(oldEmbed)
            val embedTitle = when (newStatus) {
                "DONE" -> "対応完了"
                "CANCELED" -> "対応中止"
                else -> ""
            }
            embedBuilder.setTitle(embedTitle)
            embedBuilder.setColor(if (newStatus == "DONE") Color.GREEN else Color.RED)
            embedBuilder.addField("対応者", event.user.name, false)
            when (newStatus) {
                "DONE" -> embedBuilder.addField("対応内容", inputContent, false)
                "CANCELED" -> embedBuilder.addField("中止理由", inputContent, false)
            }
            message.editMessageEmbeds(embedBuilder.build()).queue()

            val disabledRows = message.actionRows.map { row ->
                val disabledButtons = row.components.mapNotNull { comp ->
                    if (comp is Button) comp.asDisabled() else null
                }
                net.dv8tion.jda.api.interactions.components.ActionRow.of(disabledButtons)
            }
            message.editMessageComponents(disabledRows).queue()

            val replyMsg = when (newStatus) {
                "DONE" -> "通報 %s の対応完了"
                "CANCELED" -> "通報 %s の対応を中止しました"
                else -> ""
            }
            event.reply(String.format(replyMsg, reportId)).setEphemeral(true).queue()
        }, {
            event.reply("通報 $reportId のメッセージを取得できませんでした").setEphemeral(true).queue()
        })
    }
    */
}
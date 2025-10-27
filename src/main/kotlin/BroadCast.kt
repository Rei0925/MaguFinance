package com.github.rei0925

import com.github.rei0925.manager.BankManager
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.modals.Modal
import java.awt.Color
import java.sql.Connection

class BroadCast(
    private val jda: JDA,
    private val connection: Connection,
    private val bankManager: BankManager
): ListenerAdapter() {

    init {
        val stmt = connection.createStatement()
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS broadcast_channels (
                guild_id BIGINT PRIMARY KEY,
                channel_id BIGINT NOT NULL
            )
        """.trimIndent())
        stmt.close()
    }

    private val tempBroadcastData = mutableMapOf<String, Pair<String, String>>() // userId -> (subject, body)

    fun registerBroadcastChannel(guildId: Long, channelId: Long) {
        val sql = """
            INSERT INTO broadcast_channels (guild_id, channel_id)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE channel_id = VALUES(channel_id)
        """.trimIndent()
        val pstmt = connection.prepareStatement(sql)
        pstmt.setLong(1, guildId)
        pstmt.setLong(2, channelId)
        pstmt.executeUpdate()
        pstmt.close()
    }

    fun setButton(channelId: Long){
        val ch = jda.getTextChannelById(channelId)
        if (ch == null){
            println("チャンネルが見つかりませんでした。")
            return
        }

        val embed = EmbedBuilder()
            .setTitle("お知らせ投稿チャンネルに管理者が指定しました。")
            .setColor(Color.GREEN)
            .setFooter("MaguFinanceBroadCast")
            .build()

        val button: Button = Button.primary("broad_cast_button", "お知らせを投稿")

        ch.sendMessageEmbeds(embed)
            .setComponents(ActionRow.of(button))
            .queue()
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        when (event.componentId) {
            "broad_cast_button" -> {
                val subject = TextInput.create("subject", TextInputStyle.SHORT)
                    .setPlaceholder("お知らせの題名を入力してください")
                    .setMinLength(10)
                    .setMaxLength(100) // or setRequiredRange(10, 100)
                    .build()

                val body = TextInput.create("body", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("お知らせの内容を入力してください")
                    .setMinLength(30)
                    .setMaxLength(1000)
                    .build()

                /**
                val bar = TextDisplay.of(
                    """
**══════════════════════════**
ここから下は詳細設定用です。
メンション設定はeveryoneメンションが設定されるかの違いです。
NO  -> メンションしません。
YES -> メンションします。
                    """)

                val exclamation = Emoji.fromUnicode("❗")
                val x = Emoji.fromUnicode("❌")

                val mention = StringSelectMenu.create("mention")
                    .addOption("NO","no",x)
                    .addOption("YES","yes",exclamation)
                    .build()
                */

                val modal = Modal.create("bc", "お知らせ")
                    .addComponents(Label.of("Subject", subject), Label.of("Body", body))
                    .build()

                event.replyModal(modal).queue()
            }
            "bc_done" -> {
                val data = tempBroadcastData[event.user.id]
                if (data == null) {
                    event.reply("投稿データが見つかりませんでした").setEphemeral(true).queue()
                    return
                }
                val (subject, body) = data

                // DBから登録済みチャンネルを取得
                val stmt = connection.createStatement()
                val rs = stmt.executeQuery("SELECT guild_id, channel_id FROM broadcast_channels")
                val channels = mutableListOf<Long>()
                while (rs.next()) {
                    val channelId = rs.getLong("channel_id")
                    channels.add(channelId)
                }
                rs.close()
                stmt.close()

                // 全チャンネルに投稿
                val embed = EmbedBuilder()
                    .setTitle(subject)
                    .setDescription(body)
                    .setColor(Color.CYAN)
                    .setFooter("MaguFinanceお知らせ")
                    .setTimestamp(java.time.OffsetDateTime.now())
                    .build()

                channels.forEach { chId ->
                    val ch = jda.getTextChannelById(chId)
                    ch?.sendMessageEmbeds(embed)?.queue()
                }

                // 一時データ削除
                tempBroadcastData.remove(event.user.id)
                event.reply("すべての登録チャンネルに投稿しました").setEphemeral(true).queue()
            }
            "bc_cancel" -> {
                tempBroadcastData.remove(event.user.id)
                event.reply("投稿をキャンセルしました").setEphemeral(true).queue()
            }
        }
    }
    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (event.modalId == "bc") {
            val subjectMapping = event.getValue("subject") ?: return
            val bodyMapping = event.getValue("body") ?: return

            val subject = subjectMapping.asString
            val body = bodyMapping.asString

            tempBroadcastData[event.user.id] = Pair(subject, body) // 一時保存

            val doneButton: Button = Button.success("bc_done", "投稿")
            val cancelButton = Button.danger("bc_cancel", "キャンセル")

            event.reply("以下の内容でお知らせを投稿しますか?\n# $subject\n$body")
                .setEphemeral(true)
                .setComponents(ActionRow.of(doneButton,cancelButton))
                .queue()
        }
    }
}
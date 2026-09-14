package com.ruskserver.moveearth_addtional.s2.notification.discord;

import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.time.Instant;
import java.util.List;

/** Central embed style used by every normal bot response and nation notification. */
public final class MoveEarthDiscordEmbeds {
    private static final int ACCENT = 0x24C8A5;
    private static final int SUCCESS = 0x39D98A;
    private static final int WARNING = 0xF2C94C;
    private static final int DANGER = 0xEB5757;
    private static final int MUTED = 0x7A828E;

    private MoveEarthDiscordEmbeds() { }

    public static MessageEmbed status(String serverName, int players, boolean linked, int pending) {
        return base("MoveEarth Bot Status", SUCCESS)
                .setDescription("Discord連携は正常に稼働しています。")
                .addField("Server", DiscordText.safe(serverName), true)
                .addField("Online", Integer.toString(Math.max(0, players)), true)
                .addField("This Discord", linked ? "Linked" : "Not linked", true)
                .addField("Queue", Integer.toString(Math.max(0, pending)), true)
                .build();
    }

    public static MessageEmbed linkCode(String code, long expiresAtMillis) {
        return base("国家とDiscordをリンク", ACCENT)
                .setDescription("ゲーム内の「国家通知・Discord連携」画面へ、次のコードを入力してください。")
                .addField("One-time code", "`" + DiscordText.safe(code) + "`", false)
                .addField("有効期限", "<t:" + Math.max(0L, expiresAtMillis / 1000L) + ":R>", false)
                .setFooter("コードは一度だけ使用でき、Bot再起動時にも破棄されます")
                .build();
    }

    public static MessageEmbed accountLinkCode(String code, long expiresAtMillis) {
        return base("Minecraftアカウントを本人確認", ACCENT)
                .setDescription("ゲーム内の「国家通知・Discord連携」画面で「本人確認」を選んでください。")
                .addField("One-time code", "`" + DiscordText.safe(code) + "`", false)
                .addField("有効期限", "<t:" + Math.max(0L, expiresAtMillis / 1000L) + ":R>", false)
                .setFooter("1つのMinecraft UUIDとDiscordアカウントを一対一で関連付けます")
                .build();
    }

    public static MessageEmbed operation(String title, String description, boolean success) {
        return base(title, success ? SUCCESS : DANGER)
                .setDescription(DiscordText.safe(description)).build();
    }

    public static MessageEmbed audit(List<NationNotificationSavedData.AuditEntry> entries) {
        EmbedBuilder embed = base("Discord連携 監査ログ", MUTED);
        if (entries.isEmpty()) return embed.setDescription("記録はありません。").build();
        for (int index = 0; index < entries.size() && index < 8; index++) {
            NationNotificationSavedData.AuditEntry entry = entries.get(index);
            String state = entry.success() ? "成功" : "失敗";
            String detail = DiscordText.safe(entry.detail());
            embed.addField("<t:" + (entry.atMillis() / 1000L) + ":R> • "
                    + DiscordText.safe(entry.action()), state + " — " + detail, false);
        }
        return embed.build();
    }

    public static MessageEmbed permissionDenied() {
        return base("権限がありません", DANGER)
                .setDescription("この操作に必要なDiscord管理権限がありません。")
                .build();
    }

    public static MessageEmbed guildOnly() {
        return base("サーバー内で実行してください", WARNING)
                .setDescription("このコマンドはDiscordサーバーの通知先チャンネルでのみ利用できます。")
                .build();
    }

    public static MessageEmbed channelUnavailable() {
        return base("このチャンネルへ送信できません", DANGER)
                .setDescription("Botに「チャンネルを見る」「メッセージを送信」「埋め込みリンク」の権限を付与してください。")
                .build();
    }

    public static MessageEmbed unavailable() {
        return base("MoveEarth Bot is unavailable", MUTED)
                .setDescription("Minecraftサーバーは停止中、または起動処理中です。")
                .build();
    }

    public static MessageEmbed notification(String nationName,
                                             NationNotificationSavedData.Delivery delivery) {
        EventPresentation presentation = presentation(delivery.type());
        EmbedBuilder embed = base(presentation.title, presentation.color)
                .setDescription("**" + DiscordText.safe(nationName) + "** への国家通知です。")
                .setTimestamp(Instant.ofEpochMilli(delivery.createdAtMillis()))
                .setFooter("MoveEarth event • " + delivery.id());
        if (delivery.dimension() != null) {
            embed.addField("Dimension", DiscordText.safe(delivery.dimension().toString()), true);
        }
        if (delivery.pos() != null) {
            embed.addField("Position", delivery.pos().getX() + ", " + delivery.pos().getY()
                    + ", " + delivery.pos().getZ(), true);
        }
        List<String> arguments = delivery.arguments();
        for (int index = 0; index < arguments.size() && index < 8; index++) {
            embed.addField("Detail " + (index + 1), DiscordText.safe(arguments.get(index)), false);
        }
        return embed.build();
    }

    private static EmbedBuilder base(String title, int color) {
        return new EmbedBuilder().setAuthor("MoveEarth").setTitle(title).setColor(color);
    }

    private static EventPresentation presentation(NationNotificationSavedData.EventType type) {
        return switch (type) {
            case SIEGE_INITIAL_STARTED -> new EventPresentation("Siege準備開始", WARNING);
            case SIEGE_STARTED -> new EventPresentation("Siege開始", DANGER);
            case CORE_DAMAGED -> new EventPresentation("領土コアが攻撃されています", DANGER);
            case CORE_FALLEN -> new EventPresentation("領土コア陥落", DANGER);
            case TERRITORY_EXPOSED -> new EventPresentation("領土の漏出を検出", WARNING);
            case TERRITORY_RESEALED -> new EventPresentation("領土の封鎖を確認", SUCCESS);
            case UPKEEP_WARNING -> new EventPresentation("維持費警告", WARNING);
            case JOIN_APPLICATION -> new EventPresentation("国家加入申請", ACCENT);
            case COUNTEROFFENSIVE_STARTED -> new EventPresentation("反攻開始", WARNING);
            case COUNTEROFFENSIVE_SUCCEEDED -> new EventPresentation("反攻成功", SUCCESS);
            case COUNTEROFFENSIVE_FAILED -> new EventPresentation("反攻失敗", DANGER);
            case SIEGE_ENDED -> new EventPresentation("Siege終了", MUTED);
            case TERRITORY_LOST -> new EventPresentation("領土喪失", DANGER);
            case TERRITORY_OCCUPIED -> new EventPresentation("領土占領", ACCENT);
            case SYSTEM -> new EventPresentation("システム通知", ACCENT);
        };
    }

    private record EventPresentation(String title, int color) { }
}

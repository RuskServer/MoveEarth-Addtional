package com.ruskserver.moveearth_addtional.s2.notification.discord;

import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationPresentation;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationSeverity;
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
        return base("Minecraftアカウントを連携", ACCENT)
                .setDescription("ゲーム内の「国家通知・Discord連携」画面で「アカウント連携」を選んでください。")
                .addField("One-time code", "`" + DiscordText.safe(code) + "`", false)
                .addField("有効期限", "<t:" + Math.max(0L, expiresAtMillis / 1000L) + ":R>", false)
                .setFooter("1つのMinecraft UUIDとDiscordアカウントを一対一で関連付けます")
                .build();
    }

    public static MessageEmbed setup(boolean accountLinked, boolean nationLinked,
                                     String channelName, String roleName) {
        return base("MoveEarth Discord セットアップ", ACCENT)
                .setDescription("下のボタンから必要な操作を選んでください。コードが必要な時だけ発行します。")
                .addField("個人アカウント連携", accountLinked ? "連携済み" : "未連携", true)
                .addField("国家リンク", nationLinked ? "接続済み" : "未接続", true)
                .addField("通知先", channelName == null || channelName.isBlank() ? "未設定" : DiscordText.safe(channelName), true)
                .addField("Siegeメンション", roleName == null || roleName.isBlank() ? "なし" : DiscordText.safe(roleName), true)
                .setFooter("アカウント連携は個人操作、国家設定はゲーム内の通知管理権限が必要です")
                .build();
    }

    public static MessageEmbed confirmation(String subject) {
        return base("解除の確認", DANGER)
                .setDescription(DiscordText.safe(subject) + "を解除します。通知やアカウント連携が利用できなくなります。")
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
        NotificationPresentation presentation = NotificationPresentation.from(delivery, nationName);
        int color = switch (presentation.severity()) {
            case URGENT -> DANGER;
            case WARNING -> WARNING;
            case NORMAL -> ACCENT;
        };
        EmbedBuilder embed = base(presentation.title(), color)
                .setDescription(DiscordText.safe(presentation.description()))
                .setTimestamp(Instant.ofEpochMilli(delivery.createdAtMillis()))
                .setFooter(categoryName(presentation) + " • " + severityName(presentation.severity()));
        for (NotificationPresentation.Field field : presentation.fields()) {
            embed.addField(DiscordText.safe(field.name()), DiscordText.safe(field.value()), true);
        }
        return embed.build();
    }

    private static String categoryName(NotificationPresentation presentation) {
        return switch (presentation.category()) {
            case DEFENSE -> "防衛・Siege";
            case TERRITORY -> "領土・維持";
            case CITIZENS -> "国民";
            case RECOVERY -> "復興";
            case DISPATCH -> "派遣契約";
        };
    }

    private static String severityName(NotificationSeverity severity) {
        return switch (severity) {
            case URGENT -> "緊急";
            case WARNING -> "警告";
            case NORMAL -> "通常";
        };
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
            case RECOVERY_STARTED -> new EventPresentation("復興計画開始", WARNING);
            case RECOVERY_OBJECTIVE -> new EventPresentation("復興目標達成", SUCCESS);
            case RECOVERY_COMPLETED -> new EventPresentation("復興完了", SUCCESS);
            case RECOVERY_EXPIRED -> new EventPresentation("復興支援期限終了", MUTED);
            case DISPATCH_CREATED -> new EventPresentation("派遣契約作成", ACCENT);
            case DISPATCH_ACTIVATED -> new EventPresentation("派遣契約発効", WARNING);
            case DISPATCH_COMPLETED -> new EventPresentation("派遣契約完了", SUCCESS);
            case DISPATCH_CANCELLED -> new EventPresentation("派遣契約終了", MUTED);
            case RIVAL_UPDATED -> new EventPresentation("宿敵設定更新", DANGER);
            case DIGEST -> new EventPresentation("国家通知のまとめ", ACCENT);
            case TERRITORY_INTRUSION -> new EventPresentation("領土内での破壊", WARNING);
            case VEHICLE_ATTACKED -> new EventPresentation("車両への攻撃", WARNING);
            case SYSTEM -> new EventPresentation("システム通知", ACCENT);
        };
    }

    private record EventPresentation(String title, int color) { }
}

package com.ruskserver.moveearth_addtional.s2.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** JDA-independent, typed display model. Legacy positional payloads are decoded here once. */
public record NotificationPresentation(String title, String description,
                                       NotificationCategory category, NotificationSeverity severity,
                                       List<Field> fields) {
    public NotificationPresentation {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public static NotificationPresentation from(NationNotificationSavedData.Delivery delivery,
                                                String nationName) {
        var type = delivery.type();
        var category = NotificationCategory.of(type);
        var severity = severity(type);
        List<String> args = delivery.arguments();
        List<Field> fields = new ArrayList<>();
        switch (type) {
            case SIEGE_STARTED -> {
                add(fields, "攻撃側", arg(args, 0, "不明"));
                add(fields, "防衛側", arg(args, 1, nationName));
            }
            case CORE_DAMAGED, CORE_FALLEN -> {
                String current = arg(args, 0, "?");
                String maximum = arg(args, 1, "?");
                add(fields, "耐久値", current + " / " + maximum + healthPercent(current, maximum));
            }
            case JOIN_APPLICATION -> add(fields, "申請者", arg(args, 0, "不明"));
            case UPKEEP_WARNING -> add(fields, "状態", friendlyReason(arg(args, 0, "warning")));
            case DISPATCH_CREATED -> add(fields, "契約", friendlyReason(arg(args, 1, "作成済み")));
            case DISPATCH_COMPLETED, DISPATCH_CANCELLED -> {
                add(fields, "報酬", arg(args, 0, "0"));
                add(fields, "結果", friendlyReason(arg(args, 1, "完了")));
            }
            case RECOVERY_OBJECTIVE -> add(fields, "達成内容", friendlyReason(arg(args, 0, "目標更新")));
            case RIVAL_UPDATED -> add(fields, "宿敵", friendlyReason(arg(args, 0, "解除")));
            case TERRITORY_OCCUPIED -> {
                add(fields, "旧所有国", arg(args, 0, "不明"));
                add(fields, "占領国", arg(args, 1, nationName));
            }
            case TERRITORY_LOST -> add(fields, "相手", friendlyReason(arg(args, 0, "不明")));
            case SIEGE_ENDED -> add(fields, "結果", friendlyReason(arg(args, 0, "終了")));
            case DIGEST -> {
                add(fields, "まとめ", arg(args, 0, "通知をまとめました"));
                add(fields, "件数", arg(args, 1, "1"));
            }
            default -> {
                if (!args.isEmpty()) add(fields, "内容", friendlyReason(args.get(0)));
            }
        }
        if (delivery.dimension() != null) add(fields, "ディメンション", dimensionName(delivery.dimension().toString()));
        if (delivery.pos() != null) add(fields, "地点", delivery.pos().getX() + ", "
                + delivery.pos().getY() + ", " + delivery.pos().getZ());
        return new NotificationPresentation(eventTitle(type), description(type, nationName), category, severity, fields);
    }

    public static NotificationSeverity severity(NationNotificationSavedData.EventType type) {
        return switch (type) {
            case SIEGE_STARTED, CORE_DAMAGED, CORE_FALLEN, TERRITORY_LOST -> NotificationSeverity.URGENT;
            case SIEGE_INITIAL_STARTED, TERRITORY_EXPOSED, UPKEEP_WARNING,
                    COUNTEROFFENSIVE_STARTED, COUNTEROFFENSIVE_FAILED, RECOVERY_STARTED,
                    DISPATCH_ACTIVATED -> NotificationSeverity.WARNING;
            default -> NotificationSeverity.NORMAL;
        };
    }

    public static boolean mandatory(NationNotificationSavedData.EventType type) {
        return type == NationNotificationSavedData.EventType.SIEGE_STARTED
                || type == NationNotificationSavedData.EventType.CORE_FALLEN
                || type == NationNotificationSavedData.EventType.TERRITORY_LOST;
    }

    public static String eventTitle(NationNotificationSavedData.EventType type) {
        return switch (type) {
            case SIEGE_INITIAL_STARTED -> "Siege準備が始まりました";
            case SIEGE_STARTED -> "Siegeが始まりました";
            case CORE_DAMAGED -> "領土コアが攻撃されています";
            case CORE_FALLEN -> "領土コアが陥落しました";
            case TERRITORY_EXPOSED -> "領土の漏出を検出しました";
            case TERRITORY_RESEALED -> "領土コアの再密閉を確認しました";
            case UPKEEP_WARNING -> "維持費の確認が必要です";
            case JOIN_APPLICATION -> "国家加入申請が届きました";
            case COUNTEROFFENSIVE_STARTED -> "反攻が始まりました";
            case COUNTEROFFENSIVE_SUCCEEDED -> "反攻に成功しました";
            case COUNTEROFFENSIVE_FAILED -> "反攻に失敗しました";
            case SIEGE_ENDED -> "Siegeが終了しました";
            case TERRITORY_LOST -> "領土を喪失しました";
            case TERRITORY_OCCUPIED -> "領土を占領しました";
            case RECOVERY_STARTED -> "復興計画が始まりました";
            case RECOVERY_OBJECTIVE -> "復興目標を達成しました";
            case RECOVERY_COMPLETED -> "復興が完了しました";
            case RECOVERY_EXPIRED -> "復興支援期間が終了しました";
            case DISPATCH_CREATED -> "派遣契約が作成されました";
            case DISPATCH_ACTIVATED -> "派遣契約が発効しました";
            case DISPATCH_COMPLETED -> "派遣契約が完了しました";
            case DISPATCH_CANCELLED -> "派遣契約が終了しました";
            case RIVAL_UPDATED -> "宿敵設定が更新されました";
            case DIGEST -> "国家通知のまとめ";
            case SYSTEM -> "MoveEarthからのお知らせ";
        };
    }

    private static String description(NationNotificationSavedData.EventType type, String nationName) {
        String nation = nationName == null || nationName.isBlank() ? "所属国家" : nationName;
        return switch (type) {
            case CORE_DAMAGED -> nation + "の防衛状況を確認してください。";
            case CORE_FALLEN, TERRITORY_LOST -> nation + "で重大な戦況変化が発生しました。";
            case JOIN_APPLICATION -> nation + "の加入申請をゲーム内で確認してください。";
            case DIGEST -> nation + "で発生した通常通知をまとめました。";
            default -> nation + "に関係するイベントです。";
        };
    }

    private static String arg(List<String> args, int index, String fallback) {
        if (args == null || index < 0 || index >= args.size() || args.get(index).isBlank()) return fallback;
        return args.get(index);
    }

    private static void add(List<Field> fields, String name, String value) {
        if (value != null && !value.isBlank()) fields.add(new Field(name, value));
    }

    private static String healthPercent(String current, String maximum) {
        try {
            int now = Integer.parseInt(current), max = Integer.parseInt(maximum);
            return max <= 0 ? "" : "（" + Math.max(0, Math.min(100, now * 100 / max)) + "%）";
        } catch (NumberFormatException ignored) { return ""; }
    }

    static String friendlyReason(String raw) {
        if (raw == null || raw.isBlank()) return "詳細なし";
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "critical" -> "重大な未払い";
            case "delinquent" -> "未払い";
            case "grace" -> "猶予期間";
            case "attacker_withdrew" -> "攻撃側が撤退";
            case "defender_surrendered" -> "防衛側が降伏";
            case "settled" -> "講和成立";
            case "neutralized" -> "中立化";
            case "cleared" -> "解除";
            case "resealed" -> "再密閉済み";
            case "exposed" -> "漏出中";
            default -> raw.replace('_', ' ');
        };
    }

    private static String dimensionName(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End";
            default -> id;
        };
    }

    public record Field(String name, String value) { }
}

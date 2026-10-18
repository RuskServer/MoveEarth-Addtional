package com.ruskserver.moveearth_addtional.analytics.query.export;

import java.util.Locale;
import java.util.Optional;

/**
 * 生テーブルをそのまま書き出せるエクスポート種別。SQL は固定の許可リストで、利用者の入力は
 * 期間と件数にしか入らない。プレイヤー UUID を持つ表には最後に確認した名前を添える。
 *
 * <p>各 SQL は期間の下限・上限・件数の 3 つのパラメータを取り、古い順に並ぶ。
 */
public enum ExportTable {
    PLAYERS("player_list", "プレイヤー一覧", TimeUnit.SECONDS, """
            SELECT player_uuid, last_known_name, first_seen_at, last_seen_at
            FROM player_identity
            WHERE last_seen_at BETWEEN ? AND ?
            ORDER BY last_seen_at ASC LIMIT ?"""),
    SESSIONS("sessions", "ログインセッション", TimeUnit.SECONDS, """
            SELECT session_id, player_uuid, last_known_name, joined_at, left_at,
                   online_seconds, active_seconds, afk_seconds
            FROM player_session
            WHERE joined_at BETWEEN ? AND ?
            ORDER BY joined_at ASC LIMIT ?"""),
    PLAYER_DAILY("player_daily", "プレイヤー日別活動", TimeUnit.EPOCH_DAYS, """
            SELECT a.date_epoch_day, a.player_uuid, i.last_known_name, a.dimension, a.group_owner_uuid,
                   a.active_seconds, a.distance_blocks, a.breaks, a.places, a.crafts, a.pve_kills,
                   a.pvp_kills, a.deaths, a.jobs_xp, a.tpa_successes
            FROM player_activity_daily a
            LEFT JOIN player_identity i ON i.player_uuid = a.player_uuid
            WHERE a.date_epoch_day BETWEEN ? AND ?
            ORDER BY a.date_epoch_day ASC, a.player_uuid ASC LIMIT ?"""),
    PLAYER_5M("player_5m", "プレイヤー5分別活動", TimeUnit.SECONDS, """
            SELECT a.bucket_at, a.player_uuid, i.last_known_name, a.dimension, a.group_owner_uuid,
                   a.active_seconds, a.distance_blocks, a.breaks, a.places, a.crafts, a.pve_kills,
                   a.pvp_kills, a.deaths, a.jobs_xp, a.tpa_successes
            FROM player_activity_5m a
            LEFT JOIN player_identity i ON i.player_uuid = a.player_uuid
            WHERE a.bucket_at BETWEEN ? AND ?
            ORDER BY a.bucket_at ASC, a.player_uuid ASC LIMIT ?"""),
    SPATIAL_5M("spatial_5m", "場所別活動（ヒートマップ）", TimeUnit.SECONDS, """
            SELECT bucket_at, dimension, cell_x, cell_z, y_band, group_owner_uuid, relation,
                   active_samples, unique_players
            FROM spatial_activity_5m
            WHERE bucket_at BETWEEN ? AND ?
            ORDER BY bucket_at ASC, dimension ASC, cell_x ASC, cell_z ASC LIMIT ?"""),
    DETECTORS_5M("detectors_5m", "検知器別の滞在・侵入", TimeUnit.SECONDS, """
            SELECT bucket_at, dimension, detector_pos_hash, detector_name, group_owner_uuid,
                   member_minutes, visitor_minutes, intrusion_sessions, distinct_members, distinct_visitors
            FROM detector_activity_5m
            WHERE bucket_at BETWEEN ? AND ?
            ORDER BY bucket_at ASC, detector_pos_hash ASC LIMIT ?"""),
    NATIONS("nations", "国家の推移", TimeUnit.SECONDS, """
            SELECT recorded_at, nation_uuid, name, members, online, treasury, chunks, cores, vehicles
            FROM nation_snapshot
            WHERE recorded_at BETWEEN ? AND ?
            ORDER BY recorded_at ASC, nation_uuid ASC LIMIT ?"""),
    ECONOMY("economy", "経済の推移", TimeUnit.SECONDS, """
            SELECT recorded_at, player_balances, nation_balances, escrow_balances, nations,
                   players_in_nations, online_players, open_orders
            FROM economy_snapshot
            WHERE recorded_at BETWEEN ? AND ?
            ORDER BY recorded_at ASC LIMIT ?"""),
    COLLECTOR_HEALTH("collector_health", "分析システムの稼働状況", TimeUnit.SECONDS, """
            SELECT recorded_at, queue_depth, dropped_events, last_flush_duration_ms, database_size_bytes
            FROM collector_health
            WHERE recorded_at BETWEEN ? AND ?
            ORDER BY recorded_at ASC LIMIT ?""");

    /** 1 ファイルあたりの上限。超えた分は書き出さず、切り詰めたことを報告する。 */
    public static final int ROW_LIMIT = 1_000_000;

    private final String id;
    private final String label;
    private final TimeUnit unit;
    private final String sql;

    ExportTable(String id, String label, TimeUnit unit, String sql) {
        this.id = id;
        this.label = label;
        this.unit = unit;
        this.sql = sql;
    }

    public String id() { return id; }
    public String label() { return label; }
    public String sql() { return sql; }

    /** 期間の端（エポック秒）を、この表の時刻列の単位へ変換する。 */
    public long bound(long epochSec) {
        return unit == TimeUnit.EPOCH_DAYS ? Math.floorDiv(epochSec, 86_400L) : epochSec;
    }

    public static Optional<ExportTable> byId(String id) {
        if (id == null) return Optional.empty();
        String key = id.toLowerCase(Locale.ROOT);
        for (ExportTable table : values()) {
            if (table.id.equals(key)) return Optional.of(table);
        }
        return Optional.empty();
    }

    public static String[] ids() {
        ExportTable[] tables = values();
        String[] ids = new String[tables.length];
        for (int i = 0; i < tables.length; i++) ids[i] = tables[i].id;
        return ids;
    }

    private enum TimeUnit { SECONDS, EPOCH_DAYS }
}

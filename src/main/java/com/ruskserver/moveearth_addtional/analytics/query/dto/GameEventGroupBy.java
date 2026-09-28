package com.ruskserver.moveearth_addtional.analytics.query.dto;

/** Grouping keys for game event aggregates. DAY is the calendar day in Japan time. */
public enum GameEventGroupBy {
    TYPE("type"),
    DETAIL("detail"),
    DAY("CAST((occurred_at + 32400) / 86400 AS INTEGER)"),
    NATION("nation_uuid"),
    PLAYER("player_uuid");

    private final String sql;

    GameEventGroupBy(String sql) { this.sql = sql; }

    /** The SQL expression for the key; only these fixed strings ever reach a query. */
    public String sql() { return sql; }

    public static GameEventGroupBy parse(String value) {
        if (value == null) return TYPE;
        for (GameEventGroupBy groupBy : values()) if (groupBy.name().equalsIgnoreCase(value)) return groupBy;
        return TYPE;
    }
}

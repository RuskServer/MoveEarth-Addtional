package com.ruskserver.moveearth_addtional.analytics.event;

/**
 * Kinds of Season 2 game events kept in the analytics event log. The id is what
 * the database and the web API use; the prefix groups them for the dashboard.
 */
public enum GameEventType {
    // First join and nations
    ONBOARDING_WILDERNESS("onboarding.wilderness", "荒野から開始"),
    ONBOARDING_APPLIED("onboarding.applied", "加入申請"),
    ONBOARDING_APPROVED("onboarding.approved", "加入申請の承認"),
    ONBOARDING_REJECTED("onboarding.rejected", "加入申請の拒否"),
    NATION_FOUNDED("nation.founded", "建国"),
    NATION_JOINED("nation.joined", "国家へ加入"),
    NATION_LEFT("nation.left", "国家から脱退"),
    NATION_DISSOLVED("nation.dissolved", "国家解散"),

    // First-session tutorial
    TUTORIAL_STEP("tutorial.step", "チュートリアル手順の達成"),
    TUTORIAL_COMPLETED("tutorial.completed", "チュートリアル完了"),
    TUTORIAL_SKIPPED("tutorial.skipped", "チュートリアルのスキップ"),
    TUTORIAL_RESTARTED("tutorial.restarted", "チュートリアルの再開"),

    // War
    SIEGE_INITIAL("siege.initial", "初動ロック開始"),
    SIEGE_ROLLING("siege.rolling", "ローリングSiege開始"),
    CORE_FALLEN("siege.core_fallen", "コア陥落"),
    COUNTER_CAPTURED("siege.counter_captured", "反攻による奪還"),
    FALL_SETTLED("siege.fall_settled", "陥落の確定"),
    PRISONER_TAKEN("combat.prisoner_taken", "捕虜化"),
    PRISONER_FREED("combat.prisoner_freed", "捕虜の解放"),
    COMBAT_LOGOUT("combat.logout_death", "戦闘ログアウトによる死亡"),

    // Economy
    LEDGER("economy.ledger", "TCの移動"),
    MARKET_TRADE("economy.market_trade", "市場の約定"),
    UPKEEP_PAID("economy.upkeep_paid", "維持費の支払い"),
    UPKEEP_FAILED("economy.upkeep_failed", "維持費の支払い失敗"),

    // PvE and vehicles
    WAREHOUSE_BOSS("pve.warehouse_boss", "倉庫の警備隊長討伐"),
    VEHICLE_REGISTERED("vehicle.registered", "車両コア登録");

    private final String id;
    private final String label;

    GameEventType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }
}

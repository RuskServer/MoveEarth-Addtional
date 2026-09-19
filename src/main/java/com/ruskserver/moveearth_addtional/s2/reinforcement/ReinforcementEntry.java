package com.ruskserver.moveearth_addtional.s2.reinforcement;

public record ReinforcementEntry(ReinforcementMaterial material, int durability, boolean enabled,
                                 long constructionStartedAt, long activatesAt) {
    public static final int ACTIVATION_DELAY_TICKS = 30 * 20;
    public static final int HP_FILL_TICKS = 60 * 20;

    public ReinforcementEntry {
        if (material == null) material = ReinforcementMaterial.COBBLESTONE;
        durability = Math.max(0, Math.min(material.maxDurability(), durability));
        constructionStartedAt = Math.max(0L, constructionStartedAt);
        activatesAt = Math.max(0L, activatesAt);
    }

    /** Compatibility constructor for completed reinforcements and old call sites. */
    public ReinforcementEntry(ReinforcementMaterial material, int durability, boolean enabled) {
        this(material, durability, enabled, 0L, 0L);
    }

    public static ReinforcementEntry full(ReinforcementMaterial material) {
        return new ReinforcementEntry(material, material.maxDurability(), true, 0L, 0L);
    }

    public static ReinforcementEntry pending(ReinforcementMaterial material, long gameTime) {
        return pending(material, gameTime, 0L);
    }

    public static ReinforcementEntry pending(ReinforcementMaterial material, long gameTime, long repairBlockedUntil) {
        long started = Math.max(0L, gameTime);
        return new ReinforcementEntry(material, 0, false, started,
                Math.max(started, repairBlockedUntil) + ACTIVATION_DELAY_TICKS);
    }

    public int maxDurability() {
        return material.maxDurability();
    }

    public boolean damaged() {
        return durability < maxDurability();
    }

    public ReinforcementEntry repair() {
        return new ReinforcementEntry(material,
                Math.min(maxDurability(), durability + material.repairPerItem()), enabled,
                constructionStartedAt, activatesAt);
    }

    public ReinforcementEntry damage(int amount) {
        return new ReinforcementEntry(material, durability - Math.max(0, amount), enabled,
                amount > 0 && enabled ? 0L : constructionStartedAt,
                amount > 0 && enabled ? 0L : activatesAt);
    }

    public ReinforcementEntry advance(long gameTime) {
        if (activatesAt <= 0L || (enabled && durability >= maxDurability())) return this;
        if (gameTime < activatesAt) return this;
        long elapsed = Math.max(0L, gameTime - activatesAt);
        int target = elapsed >= HP_FILL_TICKS
                ? maxDurability()
                : Math.max(1, (int) Math.ceil(maxDurability() * (elapsed + 1.0D) / HP_FILL_TICKS));
        boolean completed = target >= maxDurability();
        return new ReinforcementEntry(material, Math.max(durability, target), true,
                completed ? 0L : constructionStartedAt, completed ? 0L : activatesAt);
    }

    public long activationTicksRemaining(long gameTime) {
        return enabled || activatesAt <= 0L ? 0L : Math.max(0L, activatesAt - gameTime);
    }
}

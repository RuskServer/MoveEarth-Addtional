package com.ruskserver.moveearth_addtional.s2.reinforcement;

/** Pure color policy shared by the passive surface coating and detailed overlay. */
public final class ReinforcementVisualStyle {
    private ReinforcementVisualStyle() {
    }

    public static Style forEntry(ReinforcementMaterial material, int durability,
                                 boolean enabled, boolean detailed, long timeMillis) {
        if (!enabled) {
            float pulse = (float) (0.04D + 0.035D * Math.sin(timeMillis / 150.0D));
            return new Style(0.69F, 0.39F, 0.94F, (detailed ? 0.30F : 0.19F) + pulse);
        }
        float ratio = Math.max(0.0F, Math.min(1.0F,
                durability / (float) Math.max(1, material.maxDurability())));
        float[] base = baseColor(material);
        float damage = 1.0F - ratio;
        float red = mix(base[0], 1.0F, damage * 0.85F);
        float green = mix(base[1], 0.26F, damage * 0.85F);
        float blue = mix(base[2], 0.20F, damage * 0.85F);
        float pulse = ratio < 1.0F
                ? (float) (0.025D + 0.025D * Math.sin(timeMillis / 180.0D)) : 0.0F;
        float alpha = (detailed ? 0.29F : 0.17F) + damage * (detailed ? 0.16F : 0.11F) + pulse;
        return new Style(red, green, blue, alpha);
    }

    private static float[] baseColor(ReinforcementMaterial material) {
        return switch (material) {
            case COBBLESTONE -> new float[]{0.56F, 0.62F, 0.68F};
            case COPPER -> new float[]{0.85F, 0.48F, 0.29F};
            case IRON -> new float[]{0.76F, 0.82F, 0.86F};
            case GOLD -> new float[]{1.00F, 0.72F, 0.24F};
            case DIAMOND -> new float[]{0.28F, 0.91F, 0.88F};
        };
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * Math.max(0.0F, Math.min(1.0F, amount));
    }

    public record Style(float red, float green, float blue, float alpha) {
    }
}

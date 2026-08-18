package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

final class PvpPlayerSnapshot {
    final ListTag inventory;
    final ResourceKey<Level> dimension;
    final double x, y, z;
    final float yaw, pitch;
    final int selected;
    final GameType gameMode;
    final float health;
    final float absorption;
    final CompoundTag food;
    final int experienceLevel;
    final int totalExperience;
    final float experienceProgress;
    final ListTag effects;
    final String scoreboardTeam;

    PvpPlayerSnapshot(ServerPlayer player) {
        inventory = player.getInventory().save(new ListTag());
        dimension = player.level().dimension();
        x = player.getX();
        y = player.getY();
        z = player.getZ();
        yaw = player.getYRot();
        pitch = player.getXRot();
        selected = player.getInventory().selected;
        gameMode = player.gameMode.getGameModeForPlayer();
        health = player.getHealth();
        absorption = player.getAbsorptionAmount();
        food = new CompoundTag();
        player.getFoodData().addAdditionalSaveData(food);
        experienceLevel = player.experienceLevel;
        totalExperience = player.totalExperience;
        experienceProgress = player.experienceProgress;
        effects = new ListTag();
        for (MobEffectInstance effect : player.getActiveEffects()) effects.add(effect.save());
        scoreboardTeam = player.getTeam() == null ? "" : player.getTeam().getName();
    }

    private PvpPlayerSnapshot(CompoundTag tag) {
        inventory = tag.getList("Inventory", Tag.TAG_COMPOUND);
        dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension")));
        x = tag.getDouble("X");
        y = tag.getDouble("Y");
        z = tag.getDouble("Z");
        yaw = tag.getFloat("Yaw");
        pitch = tag.getFloat("Pitch");
        selected = Mth.clamp(tag.getInt("Selected"), 0, 8);
        gameMode = GameType.byId(tag.getInt("GameMode"));
        health = tag.getFloat("Health");
        absorption = tag.getFloat("Absorption");
        food = tag.getCompound("Food");
        experienceLevel = tag.getInt("XpLevel");
        totalExperience = tag.getInt("XpTotal");
        experienceProgress = tag.getFloat("XpProgress");
        effects = tag.getList("Effects", Tag.TAG_COMPOUND);
        scoreboardTeam = tag.getString("ScoreboardTeam");
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Inventory", inventory.copy());
        tag.putString("Dimension", dimension.location().toString());
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putInt("Selected", selected);
        tag.putInt("GameMode", gameMode.getId());
        tag.putFloat("Health", health);
        tag.putFloat("Absorption", absorption);
        tag.put("Food", food.copy());
        tag.putInt("XpLevel", experienceLevel);
        tag.putInt("XpTotal", totalExperience);
        tag.putFloat("XpProgress", experienceProgress);
        tag.put("Effects", effects.copy());
        tag.putString("ScoreboardTeam", scoreboardTeam);
        return tag;
    }

    static PvpPlayerSnapshot load(CompoundTag tag) {
        return new PvpPlayerSnapshot(tag);
    }

    void restoreState(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().load(inventory);
        player.getInventory().selected = selected;
        player.getFoodData().readAdditionalSaveData(food);
        player.experienceLevel = experienceLevel;
        player.totalExperience = totalExperience;
        player.experienceProgress = experienceProgress;
        player.removeAllEffects();
        for (int i = 0; i < effects.size(); i++) {
            MobEffectInstance effect = MobEffectInstance.load(effects.getCompound(i));
            if (effect != null) player.addEffect(effect);
        }
        player.setHealth(Mth.clamp(health, 1.0F, player.getMaxHealth()));
        player.setAbsorptionAmount(Math.max(0.0F, absorption));
    }
}

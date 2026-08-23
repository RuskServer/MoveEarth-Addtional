package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Shared, server-authoritative loadout definitions used by the PvP screen and match manager. */
public enum PvpLoadoutPreset {
    ASSAULT(
            "assault", "RA39 + G45", "265ms", "RA39 EXP3/X2 · G45 RMRHD/FL23",
            weapon(0, "ra39", "sight_exp3", "laser_x2"),
            sidearm()
    ),
    RUSHER(
            "rusher", "EF_SMG + G45", "200-267ms", "SMG RMRHD/FLX9 · G45 RMRHD/FL23",
            weapon(0, "ef_smg", "sight_rmrhd_ris", "laser_flx9"),
            sidearm()
    ),
    BREACHER(
            "breacher", "EF_SG + G45", "300ms", "SG RMRHD RIS · G45 RMRHD/FL23",
            weapon(0, "ef_sg", "sight_rmrhd_ris"),
            sidearm()
    ),
    MARKSMAN(
            "marksman", "NSR20 + G45", "333ms", "NSR MK5HD/FL23L · G45 RMRHD/FL23",
            weapon(0, "nsr20", "scope_mk5hd", "laser_fl23l"),
            sidearm()
    );

    private final String id;
    private final String weaponSummary;
    private final String bodyTtk;
    private final String attachmentSummary;
    private final List<Weapon> weapons;

    PvpLoadoutPreset(String id, String weaponSummary, String bodyTtk, String attachmentSummary,
                     Weapon... weapons) {
        this.id = id;
        this.weaponSummary = weaponSummary;
        this.bodyTtk = bodyTtk;
        this.attachmentSummary = attachmentSummary;
        this.weapons = List.copyOf(Arrays.asList(weapons));
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "screen.moveearth_addtional.pvp.loadout." + id;
    }

    public String descriptionKey() {
        return translationKey() + ".description";
    }

    public String rangeKey() {
        return translationKey() + ".range";
    }

    public String weaponSummary() {
        return weaponSummary;
    }

    public String bodyTtk() {
        return bodyTtk;
    }

    public String attachmentSummary() {
        return attachmentSummary;
    }

    public List<Weapon> weapons() {
        return weapons;
    }

    public Weapon primary() {
        return weapons.getFirst();
    }

    public static Optional<PvpLoadoutPreset> byId(String id) {
        return Arrays.stream(values()).filter(preset -> preset.id.equals(id)).findFirst();
    }

    public static PvpLoadoutPreset defaultPreset() {
        return ASSAULT;
    }

    private static Weapon sidearm() {
        return weapon(1, "g45", "sight_rmrhd", "laser_fl23");
    }

    private static Weapon weapon(int slot, String gun, String... attachments) {
        return new Weapon(slot, fmic(gun), Arrays.stream(attachments).map(PvpLoadoutPreset::fmic).toList());
    }

    private static ResourceLocation fmic(String path) {
        return ResourceLocation.fromNamespaceAndPath("fmic", path);
    }

    public record Weapon(int slot, ResourceLocation gunId, List<ResourceLocation> attachments) {
        public Weapon {
            if (slot < 0 || slot > 8) throw new IllegalArgumentException("Hotbar slot out of range: " + slot);
            attachments = List.copyOf(attachments);
        }
    }
}

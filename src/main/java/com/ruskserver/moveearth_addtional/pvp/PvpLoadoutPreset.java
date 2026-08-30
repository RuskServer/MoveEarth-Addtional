package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Shared, server-authoritative default loadout templates. */
public enum PvpLoadoutPreset {
    ASSAULT(
            "assault", "アサルト", "SCAR-L + P320", "369ms", "SCAR-L T2/PEQ-15 · P320 SRO/Compact", 0xFF5DCBFF,
            weapon(0, "scar_l", "sight_t2", "laser_peq15"),
            sidearm()
    ),
    RUSHER(
            "rusher", "ラッシャー", "MP5A5 + P320", "366ms", "MP5A5 T2/Compact · P320 SRO/Compact", 0xFFFF6577,
            weapon(0, "hk_mp5a5", "sight_t2", "laser_compact"),
            sidearm()
    ),
    BREACHER(
            "breacher", "ブリーチャー", "AA12 + P320", "343ms", "AA12 T2 · P320 SRO/Compact", 0xFFFFB454,
            weapon(0, "aa12", "sight_t2"),
            sidearm()
    ),
    MARKSMAN(
            "marksman", "マークスマン", "SKS Tactical + P320", "353ms", "SKS ELCAN · P320 SRO/Compact", 0xFF68E09B,
            weapon(0, "sks_tactical", "scope_elcan_4x"),
            sidearm()
    );

    private final String id;
    private final String defaultDisplayName;
    private final String weaponSummary;
    private final String bodyTtk;
    private final String attachmentSummary;
    private final int color;
    private final List<PvpLoadoutDefinition.WeaponDefinition> weapons;

    PvpLoadoutPreset(String id, String defaultDisplayName, String weaponSummary, String bodyTtk, String attachmentSummary,
                     int color, PvpLoadoutDefinition.WeaponDefinition... weapons) {
        this.id = id;
        this.defaultDisplayName = defaultDisplayName;
        this.weaponSummary = weaponSummary;
        this.bodyTtk = bodyTtk;
        this.attachmentSummary = attachmentSummary;
        this.color = color;
        this.weapons = List.copyOf(Arrays.asList(weapons));
    }

    public String id() { return id; }
    public String defaultDisplayName() { return defaultDisplayName; }
    public String weaponSummary() { return weaponSummary; }
    public String bodyTtk() { return bodyTtk; }
    public String attachmentSummary() { return attachmentSummary; }
    public int color() { return color; }
    public List<PvpLoadoutDefinition.WeaponDefinition> weapons() { return weapons; }

    public PvpLoadoutDefinition toDefinition() {
        List<PvpLoadoutDefinition.WeaponDefinition> copiedWeapons = new ArrayList<>();
        for (PvpLoadoutDefinition.WeaponDefinition w : weapons) {
            copiedWeapons.add(w.copy());
        }
        return new PvpLoadoutDefinition(id, defaultDisplayName, "", weaponSummary, attachmentSummary, bodyTtk, color, copiedWeapons);
    }

    public static List<PvpLoadoutDefinition> createDefaultDefinitions() {
        List<PvpLoadoutDefinition> list = new ArrayList<>();
        for (PvpLoadoutPreset preset : values()) {
            list.add(preset.toDefinition());
        }
        return list;
    }

    public static Optional<PvpLoadoutPreset> byId(String id) {
        return Arrays.stream(values()).filter(preset -> preset.id.equals(id)).findFirst();
    }

    public static PvpLoadoutPreset defaultPreset() {
        return ASSAULT;
    }

    private static PvpLoadoutDefinition.WeaponDefinition sidearm() {
        return weapon(1, "p320", "sight_sro_dot", "laser_compact");
    }

    private static PvpLoadoutDefinition.WeaponDefinition weapon(int slot, String gun, String... attachments) {
        return new PvpLoadoutDefinition.WeaponDefinition(slot, tacz(gun), Arrays.stream(attachments).map(PvpLoadoutPreset::tacz).toList());
    }

    private static ResourceLocation tacz(String path) {
        return ResourceLocation.fromNamespaceAndPath("tacz", path);
    }
}

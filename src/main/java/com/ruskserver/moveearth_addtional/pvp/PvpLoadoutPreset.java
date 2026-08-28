package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Shared, server-authoritative loadout definitions used by the PvP screen and match manager. */
public enum PvpLoadoutPreset {
    ASSAULT(
            "assault", "SCAR-L + P320", "369ms", "SCAR-L T2/PEQ-15 · P320 SRO/Compact",
            weapon(0, "scar_l", "sight_t2", "laser_peq15"),
            sidearm()
    ),
    RUSHER(
            "rusher", "MP5A5 + P320", "366ms", "MP5A5 T2/Compact · P320 SRO/Compact",
            weapon(0, "hk_mp5a5", "sight_t2", "laser_compact"),
            sidearm()
    ),
    BREACHER(
            "breacher", "AA12 + P320", "343ms", "AA12 T2 · P320 SRO/Compact",
            weapon(0, "aa12", "sight_t2"),
            sidearm()
    ),
    MARKSMAN(
            "marksman", "SKS Tactical + P320", "353ms", "SKS ELCAN · P320 SRO/Compact",
            weapon(0, "sks_tactical", "scope_elcan_4x"),
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
        return weapon(1, "p320", "sight_sro_dot", "laser_compact");
    }

    private static Weapon weapon(int slot, String gun, String... attachments) {
        return new Weapon(slot, tacz(gun), Arrays.stream(attachments).map(PvpLoadoutPreset::tacz).toList());
    }

    private static ResourceLocation tacz(String path) {
        return ResourceLocation.fromNamespaceAndPath("tacz", path);
    }

    public record Weapon(int slot, ResourceLocation gunId, List<ResourceLocation> attachments) {
        public Weapon {
            if (slot < 0 || slot > 8) throw new IllegalArgumentException("Hotbar slot out of range: " + slot);
            attachments = List.copyOf(attachments);
        }
    }
}

package com.ruskserver.moveearth_addtional.pvp;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * PvPロードアウトの動的定義クラス。
 * 武器、アタッチメント、説明、カラーなどのメタデータを保持します。
 */
public class PvpLoadoutDefinition {

    private String id;
    private String displayName;
    private String description;
    private String weaponSummary;
    private String attachmentSummary;
    private String bodyTtk;
    private int color;
    private List<WeaponDefinition> weapons;

    public PvpLoadoutDefinition(String id, String displayName, String description,
                                String weaponSummary, String attachmentSummary, String bodyTtk,
                                int color, List<WeaponDefinition> weapons) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = displayName != null ? displayName : id;
        this.description = description != null ? description : "";
        this.weaponSummary = weaponSummary != null ? weaponSummary : "";
        this.attachmentSummary = attachmentSummary != null ? attachmentSummary : "";
        this.bodyTtk = bodyTtk != null ? bodyTtk : "";
        this.color = color;
        this.weapons = new ArrayList<>(weapons != null ? weapons : Collections.emptyList());
    }

    public String id() { return id; }
    public void setId(String id) { this.id = id; }

    public String displayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String description() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String weaponSummary() { return weaponSummary; }
    public void setWeaponSummary(String weaponSummary) { this.weaponSummary = weaponSummary; }

    public String attachmentSummary() { return attachmentSummary; }
    public void setAttachmentSummary(String attachmentSummary) { this.attachmentSummary = attachmentSummary; }

    public String bodyTtk() { return bodyTtk; }
    public void setBodyTtk(String bodyTtk) { this.bodyTtk = bodyTtk; }

    public int color() { return color; }
    public void setColor(int color) { this.color = color; }

    public List<WeaponDefinition> weapons() { return weapons; }
    public void setWeapons(List<WeaponDefinition> weapons) { this.weapons = new ArrayList<>(weapons); }

    public WeaponDefinition primary() {
        return weapons.isEmpty() ? null : weapons.getFirst();
    }

    public PvpLoadoutDefinition copy() {
        List<WeaponDefinition> copiedWeapons = new ArrayList<>();
        for (WeaponDefinition w : weapons) {
            copiedWeapons.add(w.copy());
        }
        return new PvpLoadoutDefinition(id, displayName, description, weaponSummary, attachmentSummary, bodyTtk, color, copiedWeapons);
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("DisplayName", displayName);
        tag.putString("Description", description);
        tag.putString("WeaponSummary", weaponSummary);
        tag.putString("AttachmentSummary", attachmentSummary);
        tag.putString("BodyTtk", bodyTtk);
        tag.putInt("Color", color);

        ListTag weaponsList = new ListTag();
        for (WeaponDefinition w : weapons) {
            weaponsList.add(w.save());
        }
        tag.put("Weapons", weaponsList);
        return tag;
    }

    public static PvpLoadoutDefinition load(CompoundTag tag, HolderLookup.Provider registries) {
        String id = tag.getString("Id");
        String displayName = tag.getString("DisplayName");
        String description = tag.getString("Description");
        String weaponSummary = tag.getString("WeaponSummary");
        String attachmentSummary = tag.getString("AttachmentSummary");
        String bodyTtk = tag.getString("BodyTtk");
        int color = tag.contains("Color") ? tag.getInt("Color") : 0xFF5DCBFF;

        List<WeaponDefinition> weapons = new ArrayList<>();
        if (tag.contains("Weapons", Tag.TAG_LIST)) {
            ListTag weaponsList = tag.getList("Weapons", Tag.TAG_COMPOUND);
            for (int i = 0; i < weaponsList.size(); i++) {
                weapons.add(WeaponDefinition.load(weaponsList.getCompound(i)));
            }
        }

        return new PvpLoadoutDefinition(id, displayName, description, weaponSummary, attachmentSummary, bodyTtk, color, weapons);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(displayName);
        buf.writeUtf(description);
        buf.writeUtf(weaponSummary);
        buf.writeUtf(attachmentSummary);
        buf.writeUtf(bodyTtk);
        buf.writeInt(color);
        buf.writeVarInt(weapons.size());
        for (WeaponDefinition w : weapons) {
            w.write(buf);
        }
    }

    public static PvpLoadoutDefinition read(RegistryFriendlyByteBuf buf) {
        String id = buf.readUtf();
        String displayName = buf.readUtf();
        String description = buf.readUtf();
        String weaponSummary = buf.readUtf();
        String attachmentSummary = buf.readUtf();
        String bodyTtk = buf.readUtf();
        int color = buf.readInt();
        int weaponCount = buf.readVarInt();
        List<WeaponDefinition> weapons = new ArrayList<>(weaponCount);
        for (int i = 0; i < weaponCount; i++) {
            weapons.add(WeaponDefinition.read(buf));
        }
        return new PvpLoadoutDefinition(id, displayName, description, weaponSummary, attachmentSummary, bodyTtk, color, weapons);
    }

    /**
     * 単一の武器スロット定義。
     */
    public static class WeaponDefinition {
        private int slot;
        private ResourceLocation gunId;
        private List<ResourceLocation> attachments;

        public WeaponDefinition(int slot, ResourceLocation gunId, List<ResourceLocation> attachments) {
            this.slot = slot;
            this.gunId = Objects.requireNonNull(gunId, "gunId");
            this.attachments = new ArrayList<>(attachments != null ? attachments : Collections.emptyList());
        }

        public int slot() { return slot; }
        public void setSlot(int slot) { this.slot = slot; }

        public ResourceLocation gunId() { return gunId; }
        public void setGunId(ResourceLocation gunId) { this.gunId = gunId; }

        public List<ResourceLocation> attachments() { return attachments; }
        public void setAttachments(List<ResourceLocation> attachments) { this.attachments = new ArrayList<>(attachments); }

        public WeaponDefinition copy() {
            return new WeaponDefinition(slot, gunId, new ArrayList<>(attachments));
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Slot", slot);
            tag.putString("GunId", gunId.toString());
            ListTag list = new ListTag();
            for (ResourceLocation att : attachments) {
                list.add(StringTag.valueOf(att.toString()));
            }
            tag.put("Attachments", list);
            return tag;
        }

        public static WeaponDefinition load(CompoundTag tag) {
            int slot = tag.getInt("Slot");
            ResourceLocation gunId = ResourceLocation.parse(tag.getString("GunId"));
            List<ResourceLocation> attachments = new ArrayList<>();
            if (tag.contains("Attachments", Tag.TAG_LIST)) {
                ListTag list = tag.getList("Attachments", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    attachments.add(ResourceLocation.parse(list.getString(i)));
                }
            }
            return new WeaponDefinition(slot, gunId, attachments);
        }

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(slot);
            buf.writeResourceLocation(gunId);
            buf.writeVarInt(attachments.size());
            for (ResourceLocation att : attachments) {
                buf.writeResourceLocation(att);
            }
        }

        public static WeaponDefinition read(RegistryFriendlyByteBuf buf) {
            int slot = buf.readVarInt();
            ResourceLocation gunId = buf.readResourceLocation();
            int count = buf.readVarInt();
            List<ResourceLocation> attachments = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                attachments.add(buf.readResourceLocation());
            }
            return new WeaponDefinition(slot, gunId, attachments);
        }

        /**
         * ItemStack から TaCZ の銃と装着されているアタッチメントを解析して生成します。
         */
        public static WeaponDefinition fromItemStack(int slot, ItemStack stack) {
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun == null) return null;

            ResourceLocation gunId = gun.getGunId(stack);
            List<ResourceLocation> attachments = new ArrayList<>();

            for (AttachmentType type : AttachmentType.values()) {
                if (type == AttachmentType.NONE) continue;
                ResourceLocation attachmentId = gun.getAttachmentId(stack, type);
                if (attachmentId != null && !attachmentId.getPath().isEmpty() && !attachmentId.getPath().equals("empty")) {
                    attachments.add(attachmentId);
                }
            }

            return new WeaponDefinition(slot, gunId, attachments);
        }
    }
}

package com.ruskserver.moveearth_addtional.entity;

import com.ruskserver.moveearth_addtional.entity.ai.RaiderShootGoal;
import com.ruskserver.moveearth_addtional.entity.ai.RaiderRole;
import com.ruskserver.moveearth_addtional.raid.AirshipRaidDifficulty;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Optional;

public final class AirshipRaiderEntity extends Zombie {
    private AirshipRaidDifficulty raidDifficulty = AirshipRaidDifficulty.NORMAL;
    private RaiderRole role = RaiderRole.RIFLEMAN;
    private int raidId;

    public AirshipRaiderEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        setCanPickUpLoot(false);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new RaiderShootGoal(this, 32.0F));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 40.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(AirshipRaiderEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
                entity -> entity instanceof Player player && !player.isCreative() && !player.isSpectator()));
    }

    public void equipRaidLoadout(AirshipRaidDifficulty difficulty) {
        this.raidDifficulty = difficulty;
        boolean diamond = difficulty != AirshipRaidDifficulty.NORMAL;
        int enchantmentLevel = difficulty == AirshipRaidDifficulty.LARGE ? 30 : diamond ? 24 : 18;
        equipEnchantedArmor(diamond, enchantmentLevel);
        equipGun(difficulty);
        if (difficulty == AirshipRaidDifficulty.LARGE) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(60.0D);
            setHealth(60.0F);
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.35D);
        }
    }

    private void equipEnchantedArmor(boolean diamond, int level) {
        ItemStack helmet = new ItemStack(diamond ? Items.DIAMOND_HELMET : Items.IRON_HELMET);
        ItemStack chest = new ItemStack(diamond ? Items.DIAMOND_CHESTPLATE : Items.IRON_CHESTPLATE);
        ItemStack legs = new ItemStack(diamond ? Items.DIAMOND_LEGGINGS : Items.IRON_LEGGINGS);
        ItemStack boots = new ItemStack(diamond ? Items.DIAMOND_BOOTS : Items.IRON_BOOTS);
        setItemSlot(EquipmentSlot.HEAD, enchant(helmet, level));
        setItemSlot(EquipmentSlot.CHEST, enchant(chest, level));
        setItemSlot(EquipmentSlot.LEGS, enchant(legs, level));
        setItemSlot(EquipmentSlot.FEET, enchant(boots, level));
    }

    private ItemStack enchant(ItemStack stack, int level) {
        return EnchantmentHelper.enchantItem(getRandom(), stack, level, registryAccess(), Optional.empty());
    }

    private void equipGun(AirshipRaidDifficulty difficulty) {
        ItemStack gunStack = new ItemStack(ModItems.MODERN_KINETIC_GUN.get());
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun != null) {
            String gunName = switch (role) {
                case RIFLEMAN -> "hk416d";
                case FLANKER -> "m4a1";
                case HEAVY -> "m249";
            };
            gun.setGunId(gunStack, ResourceLocation.fromNamespaceAndPath("tacz", gunName));
            gun.setCurrentAmmoCount(gunStack, role == RaiderRole.HEAVY ? 75 : 30);
            gun.setBulletInBarrel(gunStack, true);
            int reserveAmmo = role == RaiderRole.HEAVY ? 150 : 90;
            gun.setMaxDummyAmmoAmount(gunStack, reserveAmmo);
            gun.setDummyAmmoAmount(gunStack, reserveAmmo);
            installAttachment(gun, gunStack, "sight_t2");
            installAttachment(gun, gunStack, role == RaiderRole.RIFLEMAN
                    ? "grip_vertical_military" : "grip_cobra");
            installAttachment(gun, gunStack, role == RaiderRole.HEAVY
                    ? "muzzle_brake_pioneer" : "muzzle_silencer_ursus");
            if (role != RaiderRole.HEAVY) {
                installAttachment(gun, gunStack, "extended_mag_2");
            }
        }
        setItemSlot(EquipmentSlot.MAINHAND, gunStack);
    }

    private void installAttachment(IGun gun, ItemStack gunStack, String attachmentName) {
        ItemStack attachmentStack = new ItemStack(ModItems.ATTACHMENT.get());
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
        if (attachment == null) return;
        attachment.setAttachmentId(attachmentStack,
                ResourceLocation.fromNamespaceAndPath("tacz", attachmentName));
        try {
            gun.installAttachment(registryAccess(), gunStack, attachmentStack);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    protected void dropEquipment() {
        float gunChance = switch (raidDifficulty) {
            case NORMAL -> 0.15F;
            case ELITE -> 0.25F;
            case LARGE -> 0.35F;
        };
        float armorChance = switch (raidDifficulty) {
            case NORMAL -> 0.10F;
            case ELITE -> 0.15F;
            case LARGE -> 0.20F;
        };
        dropSlotWithFixedChance(EquipmentSlot.MAINHAND, gunChance);
        dropSlotWithFixedChance(EquipmentSlot.HEAD, armorChance);
        dropSlotWithFixedChance(EquipmentSlot.CHEST, armorChance);
        dropSlotWithFixedChance(EquipmentSlot.LEGS, armorChance);
        dropSlotWithFixedChance(EquipmentSlot.FEET, armorChance);
    }

    private void dropSlotWithFixedChance(EquipmentSlot slot, float chance) {
        ItemStack stack = getItemBySlot(slot);
        if (!stack.isEmpty() && getRandom().nextFloat() < chance) {
            spawnAtLocation(stack.copy());
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        float ammoChance = switch (raidDifficulty) {
            case NORMAL -> 0.60F;
            case ELITE -> 0.70F;
            case LARGE -> 0.80F;
        };
        if (getRandom().nextFloat() >= ammoChance) return;

        int amount = switch (raidDifficulty) {
            case NORMAL -> 16 + getRandom().nextInt(17);
            case ELITE -> 24 + getRandom().nextInt(25);
            case LARGE -> 36 + getRandom().nextInt(29);
        };
        ItemStack ammoStack = new ItemStack(ModItems.AMMO.get(), amount);
        IAmmo ammo = IAmmo.getIAmmoOrNull(ammoStack);
        if (ammo != null) {
            ammo.setAmmoId(ammoStack, ResourceLocation.fromNamespaceAndPath("tacz", "556x45"));
            spawnAtLocation(ammoStack);
        }
    }

    public AirshipRaidDifficulty getRaidDifficulty() {
        return raidDifficulty;
    }

    public int getRaidId() {
        return raidId;
    }

    public void setRaidId(int raidId) {
        this.raidId = raidId;
    }

    public RaiderRole getRole() {
        return role;
    }

    public void setRole(RaiderRole role) {
        this.role = role;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("MoveEarthRaidId", raidId);
        tag.putString("MoveEarthRaidDifficulty", raidDifficulty.name());
        tag.putString("MoveEarthRaiderRole", role.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        raidId = tag.getInt("MoveEarthRaidId");
        if (tag.contains("MoveEarthRaidDifficulty")) {
            try {
                raidDifficulty = AirshipRaidDifficulty.valueOf(tag.getString("MoveEarthRaidDifficulty"));
            } catch (IllegalArgumentException ignored) {
                raidDifficulty = AirshipRaidDifficulty.NORMAL;
            }
        }
        if (tag.contains("MoveEarthRaiderRole")) {
            try {
                role = RaiderRole.valueOf(tag.getString("MoveEarthRaiderRole"));
            } catch (IllegalArgumentException ignored) {
                role = RaiderRole.RIFLEMAN;
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!onGround() && getDeltaMovement().y < -0.9D) {
            setDeltaMovement(getDeltaMovement().x, -0.9D, getDeltaMovement().z);
            fallDistance = 0.0F;
        }
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier,
                                   net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }
}

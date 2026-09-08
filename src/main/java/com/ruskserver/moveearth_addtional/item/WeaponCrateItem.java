package com.ruskserver.moveearth_addtional.item;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WeaponCrateItem extends Item {
    public WeaponCrateItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack crate = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(crate, true);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.pass(crate);
        if (PvpMatchManager.INSTANCE.isActive(serverPlayer)) {
            player.sendSystemMessage(Component.literal("§c試合中は武器箱を開封できません。"));
            return InteractionResultHolder.fail(crate);
        }

        ItemStack reward = createReward(serverPlayer, player.getRandom());
        if (reward.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c武器箱を開封できませんでした。GunPackの読み込み状態を確認してください。"));
            return InteractionResultHolder.fail(crate);
        }
        if (!player.getAbilities().instabuild) crate.shrink(1);
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
        serverPlayer.inventoryMenu.broadcastChanges();
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        player.sendSystemMessage(Component.literal("§a武器箱から「§e" + reward.getHoverName().getString() + "§a」を獲得しました！"));
        return InteractionResultHolder.consume(crate);
    }

    private static ItemStack createReward(ServerPlayer player, RandomSource random) {
        // 有効な銃インデックスのみを抽出（dummyやデータのない内部定義を除外）
        List<ResourceLocation> loadedGuns = TimelessAPI.getAllCommonGunIndex().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().getGunData() != null)
                .map(java.util.Map.Entry::getKey)
                .filter(id -> id != null && !id.getPath().contains("dummy") && !id.getPath().isEmpty())
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();

        ResourceLocation gunId = null;
        if (!loadedGuns.isEmpty()) {
            gunId = loadedGuns.get(random.nextInt(loadedGuns.size()));
        } else {
            // フォールバック
            ResourceLocation fallback = ResourceLocation.fromNamespaceAndPath("tacz", "scar_l");
            if (TimelessAPI.getCommonGunIndex(fallback).isPresent()) {
                gunId = fallback;
            }
        }

        if (gunId == null || TimelessAPI.getCommonGunIndex(gunId).isEmpty()) {
            Moveearth_addtional.LOGGER.warn("No valid TaCZ gun found for weapon crate reward.");
            return ItemStack.EMPTY;
        }

        ItemStack gunStack = new ItemStack(com.tacz.guns.init.ModItems.MODERN_KINETIC_GUN.get());
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return ItemStack.EMPTY;
        gun.setGunId(gunStack, gunId);

        int magazineSize = TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getGunData().getAmmoAmount())
                .orElse(30);
        gun.setCurrentAmmoCount(gunStack, magazineSize);
        gun.setBulletInBarrel(gunStack, true);

        // 有効なアタッチメントのみを抽出
        List<ResourceLocation> loadedAttachments = TimelessAPI.getAllCommonAttachmentIndex().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().getData() != null)
                .map(java.util.Map.Entry::getKey)
                .filter(id -> id != null && !id.getPath().contains("dummy") && !id.getPath().isEmpty())
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();

        if (!loadedAttachments.isEmpty()) {
            int bonusCount = 1 + random.nextInt(2);
            int installed = 0;
            int attempts = 0;
            Set<AttachmentType> installedTypes = new HashSet<>();

            while (installed < bonusCount && attempts++ < 20) {
                ResourceLocation attachmentId = loadedAttachments.get(random.nextInt(loadedAttachments.size()));
                ItemStack attachmentStack = new ItemStack(com.tacz.guns.init.ModItems.ATTACHMENT.get());
                IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
                if (attachment == null) continue;
                attachment.setAttachmentId(attachmentStack, attachmentId);
                try {
                    AttachmentType type = attachment.getType(attachmentStack);
                    if (type == null || installedTypes.contains(type)) continue;
                    if (!gun.allowAttachment(gunStack, attachmentStack)) continue;
                    gun.installAttachment(player.registryAccess(), gunStack, attachmentStack);
                    installedTypes.add(type);
                    installed++;
                } catch (RuntimeException exception) {
                    Moveearth_addtional.LOGGER.debug("Attachment {} rejected for gun {}: {}", attachmentId, gunId, exception.getMessage());
                }
            }
        }

        return gunStack;
    }
}

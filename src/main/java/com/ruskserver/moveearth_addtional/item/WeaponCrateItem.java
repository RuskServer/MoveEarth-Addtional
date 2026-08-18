package com.ruskserver.moveearth_addtional.item;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.TimelessAPI;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
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

import java.util.List;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public final class WeaponCrateItem extends Item {
    public WeaponCrateItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack crate = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(crate, true);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.pass(crate);
        if (PvpMatchManager.INSTANCE.isActive(serverPlayer)) {
            player.sendSystemMessage(Component.literal("試合中は武器箱を開封できません。"));
            return InteractionResultHolder.fail(crate);
        }

        ItemStack reward = createReward(serverPlayer, player.getRandom());
        if (reward.isEmpty()) {
            player.sendSystemMessage(Component.literal("武器箱を開封できませんでした。Gunpackの読み込み状態を確認してください。"));
            return InteractionResultHolder.fail(crate);
        }
        if (!player.getAbilities().instabuild) crate.shrink(1);
        if (!player.getInventory().add(reward)) player.drop(reward, false);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        player.sendSystemMessage(Component.literal("武器箱から「" + reward.getHoverName().getString() + "」を獲得しました！"));
        return InteractionResultHolder.consume(crate);
    }

    private static ItemStack createReward(ServerPlayer player, RandomSource random) {
        List<ResourceLocation> loadedGuns = TimelessAPI.getAllCommonGunIndex().stream()
                .map(java.util.Map.Entry::getKey)
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        if (loadedGuns.isEmpty()) return ItemStack.EMPTY;
        ItemStack gunStack = new ItemStack(com.tacz.guns.init.ModItems.MODERN_KINETIC_GUN.get());
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return ItemStack.EMPTY;
        ResourceLocation gunId = loadedGuns.get(random.nextInt(loadedGuns.size()));
        gun.setGunId(gunStack, gunId);
        int magazineSize = TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getGunData().getAmmoAmount())
                .orElse(30);
        gun.setCurrentAmmoCount(gunStack, magazineSize);
        gun.setBulletInBarrel(gunStack, true);
        int bonusCount = 1 + random.nextInt(2);
        int installed = 0;
        int attempts = 0;
        Set<AttachmentType> installedTypes = new HashSet<>();
        List<ResourceLocation> loadedAttachments = TimelessAPI.getAllCommonAttachmentIndex().stream()
                .map(java.util.Map.Entry::getKey)
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        while (installed < bonusCount && attempts++ < 12) {
            if (loadedAttachments.isEmpty()) break;
            ItemStack attachmentStack = new ItemStack(com.tacz.guns.init.ModItems.ATTACHMENT.get());
            IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
            if (attachment == null) break;
            attachment.setAttachmentId(attachmentStack, loadedAttachments.get(random.nextInt(loadedAttachments.size())));
            try {
                AttachmentType type = attachment.getType(attachmentStack);
                if (installedTypes.contains(type)) continue;
                if (!gun.allowAttachment(gunStack, attachmentStack)) continue;
                gun.installAttachment(player.registryAccess(), gunStack, attachmentStack);
                installedTypes.add(type);
                installed++;
            } catch (RuntimeException ignored) {
            }
        }
        return gunStack;
    }
}

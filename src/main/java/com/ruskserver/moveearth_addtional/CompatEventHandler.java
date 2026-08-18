package com.ruskserver.moveearth_addtional;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.function.Supplier;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public class CompatEventHandler {

    private static boolean reflectionInitialized = false;
    private static Method isBleedingMethod = null;
    private static Method getDataMethod = null;
    private static Object bleedingAttachment = null;

    /**
     * リフレクションを用いてPlayerReviveのダウン状態を取得します。
     * 依存関係のコンパイルエラーを避けるための安全なアプローチです。
     */
    public static boolean isPlayerDown(Player player) {
        if (!reflectionInitialized) {
            try {
                // team.creative.playerrevive.cap.Bleeding クラスを探す
                Class<?> bleedingClass = Class.forName("team.creative.playerrevive.cap.Bleeding");
                
                // メソッドの引数型を柔軟に解決するため、メソッド一覧から検索
                Class<?> playerReviveClass = Class.forName("team.creative.playerrevive.PlayerRevive");
                Field attachmentField = playerReviveClass.getField("BLEEDING");
                Object supplier = attachmentField.get(null);
                bleedingAttachment = ((Supplier<?>) supplier).get();
                Class<?> attachmentTypeClass = Class.forName("net.neoforged.neoforge.attachment.AttachmentType");
                getDataMethod = Player.class.getMethod("getData", attachmentTypeClass);
                isBleedingMethod = bleedingClass.getMethod("isBleeding");
            } catch (Exception e) {
                System.err.println("[MoveEarth-Addtional] PlayerRevive Bleeding class reflection failed: " + e.getMessage());
            }
            reflectionInitialized = true;
        }

        if (getDataMethod != null && bleedingAttachment != null && isBleedingMethod != null) {
            try {
                Object bleedingCap = getDataMethod.invoke(player, bleedingAttachment);
                if (bleedingCap != null) {
                    return (boolean) isBleedingMethod.invoke(bleedingCap);
                }
            } catch (Exception e) {
                System.err.println("[MoveEarth-Addtional] PlayerRevive Bleeding invoke failed: " + e.getMessage());
            }
        }
        
        // フォールバック1: NBTから読み取る (PlayerReviveがNBTに書き込んでいる場合)
        if (player.getPersistentData().contains("bleeding")) {
            return player.getPersistentData().getBoolean("bleeding");
        }

        return false;
    }

    /**
     * ダウン中のプレイヤーがメインハンドにTaCZの銃を持てないように強制的にスロットを変更する
     */
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (isPlayerDown(player)) {
            var mainHandStack = player.getMainHandItem();
            if (!mainHandStack.isEmpty() && mainHandStack.getItem().builtInRegistryHolder().key().location().getNamespace().equals("tacz")) {
                int targetSlot = -1;
                for (int i = 0; i < 9; i++) {
                    var stack = player.getInventory().getItem(i);
                    if (stack.isEmpty() || !stack.getItem().builtInRegistryHolder().key().location().getNamespace().equals("tacz")) {
                        targetSlot = i;
                        break;
                    }
                }
                if (targetSlot != -1) {
                    player.getInventory().selected = targetSlot;
                }
            }
        }
    }

    /**
     * TaCZの射撃イベントをキャンセルする
     */
    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (event.getShooter() instanceof Player player) {
            if (isPlayerDown(player)) {
                if (event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
                    cancellable.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (event.getShooter() instanceof Player player) {
            if (isPlayerDown(player)) {
                if (event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
                    cancellable.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onGunMelee(GunMeleeEvent event) {
        if (event.getShooter() instanceof Player player) {
            if (isPlayerDown(player)) {
                if (event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
                    cancellable.setCanceled(true);
                }
            }
        }
    }

    /**
     * 念のため、右クリックアクション自体もキャンセルを試みる
     */
    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.RightClickItem event) {
        if (isPlayerDown(event.getEntity())) {
            // 持っているアイテムがTaCZの銃であればキャンセルする（ネームスペースで雑に判定）
            if (event.getItemStack().getItem().builtInRegistryHolder().key().location().getNamespace().equals("tacz")) {
                if (event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
                    cancellable.setCanceled(true);
                }
            }
        }
    }
}

package com.ruskserver.moveearth_addtional;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.ArrayList;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public class TimeRestrictionHandler {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final String KICK_MESSAGE = "サーバーの開放時間は日本時間の 18:00 から 00:00 までです。";
    
    // 重複処理を防ぐための状態変数
    private static int lastNotifiedMinute = -1;
    private static boolean hasKickedAtMidnight = false;

    /**
     * サーバーが現在「開放時間」かどうかを判定する (18:00 〜 23:59)
     */
    private static boolean isOpenTime(ZonedDateTime time) {
        int hour = time.getHour();
        return ServerSchedule.isOpenHour(hour);
    }

    /**
     * プレイヤーログイン時の判定
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = player.getServer();
            if (server == null || !server.isDedicatedServer()) {
                return; // シングルプレイ環境では時間制限を行わない
            }

            // OP判定 (パーミッションレベル2以上)
            if (player.hasPermissions(2)) {
                return; // OPは無条件で許可
            }

            ZonedDateTime now = ZonedDateTime.now(JST);
            if (!isOpenTime(now)) {
                // 時間外ならキック
                player.connection.disconnect(Component.literal(KICK_MESSAGE));
            }
        }
    }

    /**
     * サーバーの毎ティック処理で時刻を監視
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || !server.isDedicatedServer()) {
            return; // シングルプレイ環境では時間制限を行わない
        }
        
        // 1秒(20tick)に1回だけ処理する
        if (server.getTickCount() % 20 != 0) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(JST);
        int hour = now.getHour();
        int minute = now.getMinute();

        // クローズ時刻(00:00)の強制キック処理
        if (hour == ServerSchedule.CLOSE_HOUR) {
            if (!hasKickedAtMidnight) {
                hasKickedAtMidnight = true; // 重複キック処理防止
                
                // disconnect() can remove the player from PlayerList immediately. Iterate over
                // a snapshot so the backing list is not modified while its iterator is active.
                List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
                for (ServerPlayer player : players) {
                    player.connection.disconnect(Component.literal(KICK_MESSAGE));
                }
            }
        } else {
            hasKickedAtMidnight = false;
        }

        // 閉鎖予告の通知処理 (23時台のみ)
        if (hour == ServerSchedule.CLOSING_WARNING_HOUR) {
            // すでに通知した「分」ならスキップ
            if (lastNotifiedMinute == minute) {
                return;
            }

            String message = null;
            if (minute == 30) {
                message = "【重要】サーバーはあと30分で閉鎖されます。";
            } else if (minute == 50) {
                message = "【重要】サーバーはあと10分で閉鎖されます。安全な場所でログアウトの準備をお願いします。";
            } else if (minute == 55) {
                message = "【重要】サーバーはあと5分で閉鎖されます。";
            } else if (minute == 59) {
                message = "【重要】サーバーはあと1分で閉鎖されます！";
            }

            if (message != null) {
                lastNotifiedMinute = minute;
                
                // モダンな通知UIパケットを全プレイヤーに送信
                net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(
                        new com.ruskserver.moveearth_addtional.network.S2C_AnnouncementPacket(message)
                );
                
                // 全プレイヤーにカスタム通知音を鳴らす
                new ArrayList<>(server.getPlayerList().getPlayers()).forEach(p ->
                    p.playNotifySound(ModSounds.SERVER_NOTICE.get(), net.minecraft.sounds.SoundSource.MASTER, 1.0F, 1.0F)
                );
            }
        } else {
            // 23時台以外ならリセット
            lastNotifiedMinute = -1;
        }
    }
}

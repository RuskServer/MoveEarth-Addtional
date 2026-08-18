package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.CompatEventHandler;
import dev.firstdark.rpc.enums.ActivityType;
import dev.firstdark.rpc.models.DiscordRichPresence;
import dev.firstdark.rpc.DiscordRpc;
import dev.firstdark.rpc.handlers.RPCEventHandler;
import dev.firstdark.rpc.models.User;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DiscordRPCManager {

    // Discordデベロッパーポータルで作成したアプリケーションのクライアントIDをハードコード
    private static final String APPLICATION_ID = "1529080651469295708";
    
    // Discordサーバーの招待URLをハードコード
    private static final String DISCORD_INVITE_URL = "https://discord.gg/QNquTTTdZh";

    private static DiscordRpc rpc;
    private static long startTime;
    private static boolean isInitialized = false;
    private static boolean isReady = false;

    public static void init() {
        if (isInitialized) return;

        try {
            rpc = new DiscordRpc();
            rpc.setDebugMode(false);

            RPCEventHandler handler = new RPCEventHandler() {
                @Override
                public void ready(User user) {
                    System.out.println("[MoveEarth RPC] Discord RPC Ready for user: " + user.getUsername());
                    isReady = true;
                    // 接続が確立した後に初期状態を設定
                    updatePresence("メインメニュー", "メニュー画面");
                }
            };

            // ライブラリのシグネチャ init(String, DiscordEventHandler, boolean) に合わせる
            rpc.init(APPLICATION_ID, handler, true);
            startTime = System.currentTimeMillis() / 1000;
            isInitialized = true;

            // JVM終了時のクリーンアップ用シャットダウンフック登録
            Runtime.getRuntime().addShutdownHook(new Thread(DiscordRPCManager::shutdown));
        } catch (Exception e) {
            System.err.println("[MoveEarth RPC] Failed to initialize Discord RPC: " + e);
        }
    }

    public static void shutdown() {
        isReady = false;
        if (rpc != null) {
            try {
                rpc.shutdown(); // stop()からshutdown()へ修正
            } catch (Exception e) {
                System.err.println("[MoveEarth RPC] Error stopping Discord RPC: " + e);
            }
            rpc = null;
        }
        isInitialized = false;
    }

    public static void update() {
        if (!isInitialized || !isReady || rpc == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            // ゲーム外（タイトル画面など）
            updatePresence("メインメニュー", "メニュー画面");
        } else {
            // ゲームプレイ中
            LocalPlayer player = mc.player;
            String dimension = getDimensionFriendlyName(player.level().dimension().location().getPath());
            
            // 利敵行為を完全に防止するため、世界全体の座標ではなく、チャンク内の相対ブロック座標 (0〜15) を使用
            int rx = player.blockPosition().getX() & 15;
            int rz = player.blockPosition().getZ() & 15;
            String chunkCoord = String.format("チャンク内: [%d, %d]", rx, rz);

            // サーバー情報の判定
            String serverStatus;
            if (mc.getSingleplayerServer() != null) {
                serverStatus = "シングルプレイ";
            } else {
                ServerData serverData = mc.getCurrentServer();
                if (serverData != null) {
                    // ポート番号を除外してホスト名で判別
                    String host = serverData.ip.split(":")[0].trim();
                    if (host.equalsIgnoreCase("devbase.ruskserver.com")) {
                        serverStatus = "MoveEarth公式サーバー";
                    } else {
                        serverStatus = "マルチプレイ";
                    }
                } else {
                    serverStatus = "マルチプレイ";
                }
            }

            // ダウン（Bleeding）状態かどうかの判定
            boolean isDown = CompatEventHandler.isPlayerDown(player);

            String details;
            String state;

            if (isDown) {
                details = serverStatus + " - " + dimension;
                state = "🚨 救助待ち (ダウン中)";
            } else {
                details = serverStatus + " (" + dimension + ")";
                state = chunkCoord;
            }

            // Presenceのビルドと適用
            try {
                var builder = DiscordRichPresence.builder()
                        .details(details)
                        .state(state)
                        .largeImageKey("logo") // Discord Developer Portalにアップロードされた画像キー
                        .largeImageText("MoveEarth Mod")
                        .startTimestamp(startTime)
                        .activityType(ActivityType.PLAYING);

                // Discord参加ボタンの追加 (ビルダーの段階で設定)
                if (DISCORD_INVITE_URL != null && !DISCORD_INVITE_URL.isEmpty()) {
                    builder.button(DiscordRichPresence.RPCButton.of("Discordに参加", DISCORD_INVITE_URL));
                }

                DiscordRichPresence presence = builder.build();
                rpc.updatePresence(presence);
            } catch (Exception e) {
                System.err.println("[MoveEarth RPC] Error updating presence: " + e);
            }
        }
    }

    private static void updatePresence(String details, String state) {
        if (!isInitialized || !isReady || rpc == null) return;
        try {
            var builder = DiscordRichPresence.builder()
                    .details(details)
                    .state(state)
                    .largeImageKey("logo")
                    .largeImageText("MoveEarth Mod")
                    .startTimestamp(startTime)
                    .activityType(ActivityType.PLAYING);

            if (DISCORD_INVITE_URL != null && !DISCORD_INVITE_URL.isEmpty()) {
                builder.button(DiscordRichPresence.RPCButton.of("Discordに参加", DISCORD_INVITE_URL));
            }

            DiscordRichPresence presence = builder.build();
            rpc.updatePresence(presence);
        } catch (Exception e) {
            System.err.println("[MoveEarth RPC] Error updating initial presence: " + e);
        }
    }

    private static String getDimensionFriendlyName(String path) {
        return switch (path) {
            case "overworld" -> "地上世界";
            case "the_nether" -> "ネザー";
            case "the_end" -> "ジ・エンド";
            default -> path;
        };
    }
}

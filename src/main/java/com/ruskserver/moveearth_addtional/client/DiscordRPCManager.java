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

    /**
     * Every call into the RPC library (connect, presence writes, shutdown) runs here. They are
     * synchronous IPC to the Discord client and used to run on the client thread, where a slow or
     * restarting Discord turned into a game hitch.
     */
    private static final java.util.concurrent.ExecutorService WORKER =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "MoveEarth Discord RPC");
                thread.setDaemon(true);
                return thread;
            });
    /** Latest presence waiting to be written; newer ones replace older ones not yet sent. */
    private static final java.util.concurrent.atomic.AtomicReference<String[]> PENDING =
            new java.util.concurrent.atomic.AtomicReference<>();

    private static volatile DiscordRpc rpc;
    private static volatile long startTime;
    private static volatile boolean isInitialized = false;
    private static volatile boolean isReady = false;
    /** Last presence handed to the library, so an unchanged one costs no IPC at all. */
    private static volatile String[] lastSent;

    public static void init() {
        if (isInitialized) return;
        isInitialized = true;
        WORKER.execute(DiscordRPCManager::connect);
    }

    private static void connect() {
        try {
            rpc = new DiscordRpc();
            rpc.setDebugMode(false);

            RPCEventHandler handler = new RPCEventHandler() {
                @Override
                public void ready(User user) {
                    System.out.println("[MoveEarth RPC] Discord RPC Ready for user: " + user.getUsername());
                    isReady = true;
                    lastSent = null; // a (re)connected Discord client has no presence yet
                    // 接続が確立した後に初期状態を設定
                    updatePresence("メインメニュー", "メニュー画面");
                }
            };

            // ライブラリのシグネチャ init(String, DiscordEventHandler, boolean) に合わせる
            startTime = System.currentTimeMillis() / 1000;
            rpc.init(APPLICATION_ID, handler, true);

            // JVM終了時のクリーンアップ用シャットダウンフック登録
            Runtime.getRuntime().addShutdownHook(new Thread(DiscordRPCManager::shutdown));
        } catch (Exception e) {
            rpc = null;
            isInitialized = false;
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

    /** Reads the game state on the client thread; the IPC write happens on the worker. */
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

            updatePresence(details, state);
        }
    }

    /** Queues a presence for the worker; safe from any thread. */
    private static void updatePresence(String details, String state) {
        if (!isInitialized || !isReady || rpc == null) return;
        // Only schedule a write when none is queued; the queued one picks up the newest presence.
        if (PENDING.getAndSet(new String[]{details, state}) == null) {
            try {
                WORKER.execute(DiscordRPCManager::writePendingPresence);
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                PENDING.set(null);
            }
        }
    }

    private static void writePendingPresence() {
        String[] presenceText = PENDING.getAndSet(null);
        DiscordRpc client = rpc;
        if (presenceText == null || client == null || !isReady) return;
        if (java.util.Arrays.equals(presenceText, lastSent)) return;
        String details = presenceText[0];
        String state = presenceText[1];
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
            client.updatePresence(presence);
            lastSent = presenceText;
        } catch (Exception e) {
            System.err.println("[MoveEarth RPC] Error updating presence: " + e);
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

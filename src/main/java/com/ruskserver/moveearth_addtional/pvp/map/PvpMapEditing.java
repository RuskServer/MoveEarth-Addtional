package com.ruskserver.moveearth_addtional.pvp.map;

import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import com.ruskserver.moveearth_addtional.pvp.deploy.PvpTacticalScanner;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Admin edits to a PvP map that need checking against its battle area, shared by the commands and the area
 * wand. A change that would leave a deploy point or the hill outside the area is refused, so a map is never
 * saved in a state it cannot be played in.
 */
public final class PvpMapEditing {
    private PvpMapEditing() {
    }

    /** Sets one corner of the battle area; once both are set, the tactical map is scanned again. */
    public static boolean setCorner(ServerPlayer player, String mapId, boolean first, BlockPos pos) {
        if (!inArena(player)) return false;
        PvpMapSavedData data = PvpMapSavedData.get(player.server);
        PvpMapDefinition map = data.getById(mapId).orElse(null);
        if (map == null) {
            player.sendSystemMessage(MoveEarthMessage.error("マップID '" + mapId + "' が見つかりません。"));
            return false;
        }
        PvpMapDefinition updated = map.withBoundsCorner(first, pos);
        if (!save(player, data, updated)) return false;
        player.sendSystemMessage(MoveEarthMessage.success("マップ [" + map.displayName() + "] の戦闘エリアの角"
                + (first ? "1" : "2") + "を保存しました: " + pos.toShortString()));
        if (updated.hasOwnBounds()) {
            PvpMapGeometry.Box box = updated.bounds();
            player.sendSystemMessage(MoveEarthMessage.info("戦闘エリア: " + box.width() + "×" + box.depth()
                    + "ブロック（高さは問いません）。戦術マップは高さ " + box.minY() + "〜" + box.maxY() + " の地形から作成しています…"));
            PvpTacticalScanner.INSTANCE.request(updated, player.getUUID());
        }
        return true;
    }

    /** Saves {@code updated} unless it breaks the battle area; tells the player why when it does. */
    public static boolean save(ServerPlayer player, PvpMapSavedData data, PvpMapDefinition updated) {
        List<String> problems = updated.problems();
        if (!problems.isEmpty()) {
            player.sendSystemMessage(MoveEarthMessage.error("保存できません: " + String.join(" / ", problems)));
            return false;
        }
        data.addOrUpdate(updated);
        return true;
    }

    static boolean inArena(ServerPlayer player) {
        if (player.level().dimension().equals(PvpMatchManager.ARENA)) return true;
        player.sendSystemMessage(MoveEarthMessage.error("PvPアリーナディメンション内で実行してください。"));
        return false;
    }
}

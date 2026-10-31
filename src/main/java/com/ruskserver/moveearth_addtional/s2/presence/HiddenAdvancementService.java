package com.ruskserver.moveearth_addtional.s2.presence;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.advancement.ModCriteria;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * The rest of the light-hearted hidden advancements, each fed from where the thing happens: logging in at the
 * edges of opening hours, talking to nobody, opening the wiki over and over, a nation that lasts under an hour,
 * being imprisoned twice in a day, dying lost in someone else's land, and radiation. What
 * {@link HiddenAdvancementPolicy} counts is kept in the player's persisted data, so it carries over deaths and
 * restarts.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class HiddenAdvancementService {
    private static final String NBT_ROOT = "MoveEarthHiddenAdvancements";
    private static final String MONOLOGUE = "Monologue";
    private static final String WIKI_OPENS = "WikiOpens";
    private static final String WRONG_TURN = "WrongTurnDeaths";
    private static final String CAPTURE_DAY = "CaptureDay";
    private static final String CAPTURE_COUNT = "CaptureCount";
    private static final String FOUNDED_NATION = "FoundedNation";
    private static final String FOUNDED_AT = "FoundedAt";

    private HiddenAdvancementService() { }

    /** A login refused for being outside opening hours, just before the disconnect. */
    public static void turnedAway(ServerPlayer player) {
        ModCriteria.trigger(player, ModCriteria.CLOSED_DOOR);
    }

    /** A login let in on the dedicated server. */
    public static void loggedIn(ServerPlayer player) {
        ZonedDateTime now = ZonedDateTime.now();
        if (HiddenAdvancementPolicy.openingRush(now)) ModCriteria.trigger(player, ModCriteria.OPENING_RUSH);
        if (HiddenAdvancementPolicy.lastOrders(now)) ModCriteria.trigger(player, ModCriteria.LAST_ORDERS);
    }

    /** A local chat line nobody was in range to hear, counted whether or not the tip was shown. */
    public static void talkedToNobody(ServerPlayer sender) {
        if (count(sender, MONOLOGUE) >= HiddenAdvancementPolicy.MONOLOGUE_LINES) {
            ModCriteria.trigger(sender, ModCriteria.MONOLOGUE);
        }
    }

    public static void wikiOpened(ServerPlayer player) {
        if (count(player, WIKI_OPENS) >= HiddenAdvancementPolicy.WIKI_OPENS) {
            ModCriteria.trigger(player, ModCriteria.WIKI_REGULAR);
        }
    }

    public static void nationFounded(ServerPlayer founder, UUID nationId) {
        CompoundTag data = data(founder);
        data.putUUID(FOUNDED_NATION, nationId);
        data.putLong(FOUNDED_AT, System.currentTimeMillis());
    }

    /** A nation disbanded by {@code actor}; earns 「三日天下」 when they founded it under an hour ago. */
    public static void nationDisbanded(ServerPlayer actor, UUID nationId) {
        CompoundTag data = data(actor);
        if (!data.hasUUID(FOUNDED_NATION) || !data.getUUID(FOUNDED_NATION).equals(nationId)) return;
        long foundedAt = data.getLong(FOUNDED_AT);
        data.remove(FOUNDED_NATION);
        data.remove(FOUNDED_AT);
        if (HiddenAdvancementPolicy.shortReign(foundedAt, System.currentTimeMillis())) {
            ModCriteria.trigger(actor, ModCriteria.SHORT_REIGN);
        }
    }

    /** An online player taken to a prison. */
    public static void imprisoned(ServerPlayer captive) {
        CompoundTag data = data(captive);
        long today = HiddenAdvancementPolicy.day(System.currentTimeMillis());
        int count = HiddenAdvancementPolicy.countToday(data.getLong(CAPTURE_DAY), data.getInt(CAPTURE_COUNT), today);
        data.putLong(CAPTURE_DAY, today);
        data.putInt(CAPTURE_COUNT, count);
        if (count >= HiddenAdvancementPolicy.INMATE_CAPTURES_PER_DAY) {
            ModCriteria.trigger(captive, ModCriteria.REGULAR_INMATE);
        }
    }

    /** The player's radiation dose, checked about once a second while Mekanism is present. */
    public static void dose(ServerPlayer player, double dose) {
        if (dose >= HiddenAdvancementPolicy.GLOWING_DOSE) ModCriteria.trigger(player, ModCriteria.GLOWING);
    }

    /** Clean water just washed off the last of a dose that peaked at {@code peak} while washing. */
    public static void washedClean(ServerPlayer player, double peak) {
        if (peak >= HiddenAdvancementPolicy.BATH_FROM_DOSE) ModCriteria.trigger(player, ModCriteria.FRESH_FROM_BATH);
    }

    /** 「方向音痴」 counts deaths that were not another player's doing: lost, not beaten. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getSource().getEntity() instanceof Player) return;
        UUID land = TerritorySavedData.get(player.server)
                .controllingNation(player.server, player.level().dimension().location(), player.blockPosition())
                .orElse(null);
        UUID own = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        if (!HiddenAdvancementPolicy.foreignLand(land, own)) return;
        if (count(player, WRONG_TURN) >= HiddenAdvancementPolicy.WRONG_TURN_DEATHS) {
            ModCriteria.trigger(player, ModCriteria.WRONG_TURN);
        }
    }

    /** Adds one to a counter and returns the new total. */
    private static int count(ServerPlayer player, String key) {
        CompoundTag data = data(player);
        int total = Math.max(0, data.getInt(key)) + 1;
        data.putInt(key, total);
        return total;
    }

    private static CompoundTag data(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(NBT_ROOT, Tag.TAG_COMPOUND)) persisted.put(NBT_ROOT, new CompoundTag());
        return persisted.getCompound(NBT_ROOT);
    }
}

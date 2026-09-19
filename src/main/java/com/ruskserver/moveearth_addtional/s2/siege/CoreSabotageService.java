package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.CompatEventHandler;
import com.ruskserver.moveearth_addtional.ServerSchedule;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryCoreHealthService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Physical, interruptible engineering alternative to bringing artillery into a breached core room. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class CoreSabotageService {
    private static final Map<UUID, Charge> CHARGES = new HashMap<>();
    private CoreSabotageService() { }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!S2TerritoryConfig.coreSabotageEnabled()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || !event.getItemStack().is(ModItems.WELDING_TOOL.get())
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        var core = TerritorySavedData.get(player.server).core(player.serverLevel().dimension().location(),
                event.getPos()).orElse(null);
        if (core == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Charge existing = CHARGES.get(core.id());
        if (friendly(player, core)) {
            if (existing != null && existing.armed && existing.defender == null && capable(player)) {
                existing.defender = player.getUUID();
                existing.defenderStart = player.position();
                existing.defuseTicks = 0;
                message(player, "defusing");
            }
            return;
        }
        if (existing != null) { message(player, "occupied"); return; }
        if (!capable(player) || !open(player.serverLevel()) || !validCore(player, core)
                || !nearAndLooking(player, core.pos(), player.position())) {
            message(player, "unavailable"); return;
        }
        if (!player.getOffhandItem().is(Items.TNT) || player.getOffhandItem().getCount() < 4) {
            message(player, "materials"); return;
        }
        if (SiegeService.recordAttack(player, player.serverLevel(), core.pos(), false).siege() == null) {
            message(player, "unavailable"); return;
        }
        if (CHARGES.values().stream().anyMatch(c -> c.attacker.equals(player.getUUID()))) {
            message(player, "occupied"); return;
        }
        CHARGES.put(core.id(), new Charge(core, player));
        message(player, "installing");
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var iterator = CHARGES.values().iterator();
        while (iterator.hasNext()) {
            Charge charge = iterator.next();
            ServerLevel level = event.getServer().getLevel(charge.dimension);
            ServerPlayer attacker = event.getServer().getPlayerList().getPlayer(charge.attacker);
            var core = level == null ? null : TerritorySavedData.get(event.getServer())
                    .core(level.dimension().location(), charge.pos).orElse(null);
            // Fail closed on disconnection, shutdown, changed ownership, sealed/unloaded core or truce.
            if (level == null || attacker == null || core == null || !core.id().equals(charge.coreId)
                    || !core.nationId().equals(charge.defendingNation) || !open(level)
                    || !level.hasChunkAt(charge.pos) || !validCore(attacker, core)
                    || !SiegeSavedData.get(event.getServer()).isCoreLocked(core.id())) {
                charge.bar.removeAllPlayers(); iterator.remove(); continue;
            }
            if (!charge.armed) {
                if (!capable(attacker) || !nearAndLooking(attacker, charge.pos, charge.start)
                        || !attacker.getOffhandItem().is(Items.TNT) || attacker.getOffhandItem().getCount() < 4) {
                    message(attacker, "cancelled"); charge.bar.removeAllPlayers(); iterator.remove(); continue;
                }
                charge.ticks++;
                charge.bar.setProgress(Math.min(1F, charge.ticks / 400F));
                if (charge.ticks >= 400) {
                    attacker.getOffhandItem().shrink(4);
                    charge.armed = true; charge.ticks = 0;
                    charge.bar.setName(Component.translatable("message.moveearth_addtional.sabotage.armed"));
                }
            } else {
                ServerPlayer defender = charge.defender == null ? null
                        : event.getServer().getPlayerList().getPlayer(charge.defender);
                if (defender != null && friendly(defender, core) && capable(defender)
                        && defender.serverLevel() == level && nearAndLooking(defender, charge.pos, charge.defenderStart)) {
                    charge.defuseTicks++;
                    defender.displayClientMessage(Component.translatable(
                            "message.moveearth_addtional.sabotage.defuse_progress", (100 - charge.defuseTicks + 19) / 20), true);
                    if (charge.defuseTicks >= 100) {
                        message(defender, "defused"); charge.bar.removeAllPlayers(); iterator.remove(); continue;
                    }
                } else { charge.defender = null; charge.defuseTicks = 0; }
                charge.ticks++;
                charge.bar.setProgress(Math.max(0F, 1F - charge.ticks / 800F));
                if (charge.ticks >= 800) {
                    // Re-check attack eligibility at detonation, then use ordinary protection/scaling and fall handling.
                    if (SiegeService.recordAttack(attacker, level, charge.pos, false).siege() != null) {
                        var after = TerritoryCoreHealthService.damage(level, charge.pos,
                                Math.max(1, (int) Math.ceil(core.maximumHealth() * 0.2D)));
                        if (after != null && after.health() < core.health()) {
                            SiegeService.recordAttack(attacker, level, charge.pos, true);
                        }
                    }
                    charge.bar.removeAllPlayers(); iterator.remove(); continue;
                }
            }
            if (charge.ticks % 20 == 0) {
                Set<ServerPlayer> viewers = new HashSet<>();
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(charge.pos.getCenter()) <= 32 * 32) viewers.add(player);
                }
                for (ServerPlayer viewer : List.copyOf(charge.bar.getPlayers())) {
                    if (!viewers.contains(viewer)) charge.bar.removePlayer(viewer);
                }
                viewers.forEach(charge.bar::addPlayer);
                level.playSound(null, charge.pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS,
                        2F, charge.armed ? 1.4F : 0.7F);
            }
        }
    }

    @SubscribeEvent
    public static void hurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        CHARGES.values().removeIf(charge -> {
            if (player.getUUID().equals(charge.defender)) { charge.defender = null; charge.defuseTicks = 0; }
            if (!charge.armed && charge.attacker.equals(player.getUUID())) {
                charge.bar.removeAllPlayers(); message(player, "cancelled"); return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        CHARGES.values().forEach(charge -> charge.bar.removeAllPlayers());
        CHARGES.clear();
    }

    private static boolean open(ServerLevel level) {
        return S2TerritoryConfig.coreSabotageEnabled()
                && (!level.getServer().isDedicatedServer() || ServerSchedule.isOpenNow());
    }

    private static boolean capable(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !CompatEventHandler.isPlayerDown(player)
                && !PrisonerService.isMovementRestricted(player);
    }

    private static boolean friendly(ServerPlayer player, TerritorySavedData.CoreRecord core) {
        var nations = NationSavedData.get(player.server);
        var nation = nations.nationIdFor(player.getUUID()).orElse(null);
        return nation != null && (nation.equals(core.nationId()) || nations.isAllied(nation, core.nationId()));
    }

    private static boolean validCore(ServerPlayer player, TerritorySavedData.CoreRecord core) {
        return player.serverLevel().dimension().location().equals(core.dimension())
                && core.state() == TerritorySavedData.CoreState.EXPOSED && core.health() > 0
                && !friendly(player, core)
                && !SiegeService.peaceTruceBlocks(player, player.serverLevel(), core.pos())
                && !OfflineDefenseService.settlementProtected(player.serverLevel(), core.pos());
    }

    private static boolean nearAndLooking(ServerPlayer player, BlockPos pos, Vec3 start) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().is(ModItems.WELDING_TOOL.get())
                || player.position().distanceToSqr(start) > 0.25D
                || player.distanceToSqr(pos.getCenter()) > 16D) return false;
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = player.serverLevel().clip(new ClipContext(eye,
                eye.add(player.getLookAngle().scale(5)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.moveearth_addtional.sabotage." + key), true);
    }

    private static final class Charge {
        final UUID coreId, defendingNation, attacker;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final BlockPos pos;
        final Vec3 start;
        final ServerBossEvent bar = new ServerBossEvent(Component.translatable(
                "message.moveearth_addtional.sabotage.installing"), BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
        boolean armed;
        int ticks, defuseTicks;
        UUID defender;
        Vec3 defenderStart;
        Charge(TerritorySavedData.CoreRecord core, ServerPlayer player) {
            coreId = core.id(); defendingNation = core.nationId(); attacker = player.getUUID();
            dimension = player.serverLevel().dimension(); pos = core.pos().immutable(); start = player.position();
            bar.addPlayer(player); bar.setProgress(0F);
        }
    }
}

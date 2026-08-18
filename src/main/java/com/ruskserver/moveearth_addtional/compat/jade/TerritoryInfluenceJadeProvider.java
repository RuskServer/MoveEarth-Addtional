package com.ruskserver.moveearth_addtional.compat.jade;

import com.mojang.authlib.GameProfile;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.TerritoryCoreBlock;
import com.ruskserver.moveearth_addtional.block.TerritoryRaidBlock;
import com.ruskserver.moveearth_addtional.territory.domain.InfluenceResult;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import com.ruskserver.moveearth_addtional.territory.service.TerritoryInfluenceService;
import com.ruskserver.moveearth_addtional.territory.service.TerritoryMembershipService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

import java.util.Locale;

public enum TerritoryInfluenceJadeProvider implements IBlockComponentProvider,
        IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID,
            "territory_influence"
    );

    private static final String READY = "TerritoryReady";
    private static final String HAS_INFLUENCE = "TerritoryHasInfluence";
    private static final String OWNER_NAME = "TerritoryOwnerName";
    private static final String RELATION = "TerritoryRelation";
    private static final String LEADING_INFLUENCE = "TerritoryLeadingInfluence";
    private static final String RUNNER_UP_INFLUENCE = "TerritoryRunnerUpInfluence";
    private static final String PROTECTION_TIER = "TerritoryProtectionTier";

    private static final int RELATION_UNCONTROLLED = 0;
    private static final int RELATION_FRIENDLY = 1;
    private static final int RELATION_FOREIGN = 2;
    private static final int RELATION_CONTESTED = 3;

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (isDedicatedTerritoryBlock(accessor)
                || !(accessor.getLevel() instanceof ServerLevel level)) {
            return;
        }

        data.putBoolean(READY, true);
        InfluenceResult influence = TerritoryInfluenceService.evaluate(level, accessor.getPosition());
        if (influence.leadingOwner().isEmpty() || influence.leadingInfluence() <= 0.0D) {
            data.putBoolean(HAS_INFLUENCE, false);
            return;
        }

        TerritoryOwnerId leadingOwner = influence.leadingOwner().orElseThrow();
        data.putBoolean(HAS_INFLUENCE, true);
        data.putString(OWNER_NAME, displayOwner(level, leadingOwner));
        data.putInt(RELATION, relation(accessor, level, influence));
        data.putDouble(LEADING_INFLUENCE, influence.leadingInfluence());
        data.putDouble(RUNNER_UP_INFLUENCE, influence.runnerUpInfluence());
        data.putInt(PROTECTION_TIER, influence.protectedActions().size());
    }

    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        return !isDedicatedTerritoryBlock(accessor);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (isDedicatedTerritoryBlock(accessor)) {
            return;
        }

        CompoundTag data = accessor.getServerData();
        if (!data.getBoolean(READY)) {
            return;
        }

        IThemeHelper theme = IThemeHelper.get();
        if (!data.getBoolean(HAS_INFLUENCE)) {
            tooltip.add(theme.info(Component.translatable(
                    "jade.moveearth_addtional.territory_influence.none"
            )));
            return;
        }

        int relation = data.getInt(RELATION);
        Component territoryLine = Component.translatable(
                relationKey(relation),
                data.getString(OWNER_NAME)
        );
        tooltip.add(switch (relation) {
            case RELATION_FRIENDLY -> theme.success(territoryLine);
            case RELATION_FOREIGN -> theme.danger(territoryLine);
            case RELATION_CONTESTED -> theme.warning(territoryLine);
            default -> theme.info(territoryLine);
        });
        tooltip.add(Component.translatable(
                "jade.moveearth_addtional.territory_influence.details",
                decimal(data.getDouble(LEADING_INFLUENCE)),
                decimal(data.getDouble(RUNNER_UP_INFLUENCE)),
                data.getInt(PROTECTION_TIER)
        ));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    private static int relation(BlockAccessor accessor, ServerLevel level, InfluenceResult influence) {
        if (influence.contested()) {
            return RELATION_CONTESTED;
        }
        if (influence.controllingOwner().isEmpty()) {
            return RELATION_UNCONTROLLED;
        }
        TerritoryOwnerId controller = influence.controllingOwner().orElseThrow();
        return accessor.getPlayer() instanceof ServerPlayer player
                && TerritoryMembershipService.isMember(level, player, controller)
                ? RELATION_FRIENDLY
                : RELATION_FOREIGN;
    }

    private static String displayOwner(ServerLevel level, TerritoryOwnerId ownerId) {
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(ownerId.value());
        if (online != null) {
            return online.getScoreboardName();
        }
        return level.getServer().getProfileCache()
                .get(ownerId.value())
                .map(GameProfile::getName)
                .orElse("?");
    }

    private static String relationKey(int relation) {
        return switch (relation) {
            case RELATION_FRIENDLY -> "jade.moveearth_addtional.territory_influence.friendly";
            case RELATION_FOREIGN -> "jade.moveearth_addtional.territory_influence.foreign";
            case RELATION_CONTESTED -> "jade.moveearth_addtional.territory_influence.contested";
            default -> "jade.moveearth_addtional.territory_influence.uncontrolled";
        };
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static boolean isDedicatedTerritoryBlock(BlockAccessor accessor) {
        return accessor.getBlock() instanceof TerritoryCoreBlock
                || accessor.getBlock() instanceof TerritoryRaidBlock;
    }
}

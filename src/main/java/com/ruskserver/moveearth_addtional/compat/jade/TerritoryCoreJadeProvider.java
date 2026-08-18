package com.ruskserver.moveearth_addtional.compat.jade;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.territory.domain.InfluenceResult;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import com.ruskserver.moveearth_addtional.territory.service.TerritoryIndustrialPowerService;
import com.ruskserver.moveearth_addtional.territory.service.TerritoryInfluenceService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

public enum TerritoryCoreJadeProvider implements IBlockComponentProvider,
        IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID,
            "territory_core"
    );

    private static final String CLAIMED = "Claimed";
    private static final String OWNER_NAME = "OwnerName";
    private static final String ACTIVE = "Active";
    private static final String DIRECT_STRESS = "DirectStress";
    private static final String FACTORY_STRESS = "FactoryStress";
    private static final String CREATE_SCORE = "CreateScore";
    private static final String TOTAL_SCORE = "TotalScore";
    private static final String LEADING_INFLUENCE = "LeadingInfluence";
    private static final String RUNNER_UP_INFLUENCE = "RunnerUpInfluence";
    private static final String CONTESTED = "Contested";
    private static final String CONTROL_STATE = "ControlState";
    private static final String PROTECTION_TIER = "ProtectionTier";

    private static final int CONTROL_NONE = 0;
    private static final int CONTROL_OWN = 1;
    private static final int CONTROL_FOREIGN = 2;

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof TerritoryCoreBlockEntity core)
                || !(accessor.getLevel() instanceof ServerLevel level)) {
            return;
        }

        data.putBoolean(CLAIMED, core.getOwnerUUID() != null);
        data.putBoolean(ACTIVE, core.isActive());
        if (core.getOwnerUUID() == null) {
            return;
        }

        TerritoryOwnerId ownerId = TerritoryOwnerId.of(core.getOwnerUUID());
        TerritoryIndustrialPowerService.Breakdown power =
                TerritoryIndustrialPowerService.get(level.getServer(), ownerId);
        InfluenceResult influence = TerritoryInfluenceService.evaluate(level, accessor.getPosition());

        double directStress = power.create().directCoreStress();
        data.putString(OWNER_NAME, core.getOwnerName());
        data.putDouble(DIRECT_STRESS, directStress);
        data.putDouble(FACTORY_STRESS, Math.max(0.0D, power.create().usedStress() - directStress));
        data.putDouble(CREATE_SCORE, power.create().industrialScore());
        data.putDouble(TOTAL_SCORE, power.totalScore());
        data.putDouble(LEADING_INFLUENCE, influence.leadingInfluence());
        data.putDouble(RUNNER_UP_INFLUENCE, influence.runnerUpInfluence());
        data.putBoolean(CONTESTED, influence.contested());
        data.putInt(PROTECTION_TIER, influence.protectedActions().size());
        data.putInt(CONTROL_STATE, controlState(influence, ownerId));
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(CLAIMED)) {
            return;
        }

        if (!data.getBoolean(CLAIMED)) {
            tooltip.add(Component.translatable("jade.moveearth_addtional.territory_core.unclaimed")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.add(Component.translatable(
                "jade.moveearth_addtional.territory_core.owner",
                data.getString(OWNER_NAME)
        ));
        tooltip.add(Component.translatable(
                data.getBoolean(ACTIVE)
                        ? "jade.moveearth_addtional.territory_core.active"
                        : "jade.moveearth_addtional.territory_core.inactive"
        ).withStyle(data.getBoolean(ACTIVE) ? ChatFormatting.GREEN : ChatFormatting.RED));
        tooltip.add(Component.translatable(
                "jade.moveearth_addtional.territory_core.stress",
                decimal(data.getDouble(DIRECT_STRESS)),
                decimal(data.getDouble(FACTORY_STRESS))
        ));
        tooltip.add(Component.translatable(
                "jade.moveearth_addtional.territory_core.score",
                decimal(data.getDouble(CREATE_SCORE)),
                decimal(data.getDouble(TOTAL_SCORE))
        ));
        tooltip.add(Component.translatable(
                "jade.moveearth_addtional.territory_core.influence",
                decimal(data.getDouble(LEADING_INFLUENCE)),
                decimal(data.getDouble(RUNNER_UP_INFLUENCE))
        ));

        if (data.getBoolean(CONTESTED)) {
            tooltip.add(Component.translatable("jade.moveearth_addtional.territory_core.contested")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            int controlState = data.getInt(CONTROL_STATE);
            String stateKey = switch (controlState) {
                case CONTROL_OWN -> "jade.moveearth_addtional.territory_core.controlled";
                case CONTROL_FOREIGN -> "jade.moveearth_addtional.territory_core.foreign_control";
                default -> "jade.moveearth_addtional.territory_core.no_control";
            };
            tooltip.add(Component.translatable(stateKey).withStyle(
                    controlState == CONTROL_OWN ? ChatFormatting.AQUA : ChatFormatting.GRAY
            ));
        }

        tooltip.add(Component.translatable(
                "jade.moveearth_addtional.territory_core.protection",
                data.getInt(PROTECTION_TIER)
        ));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    private static int controlState(InfluenceResult influence, TerritoryOwnerId ownerId) {
        if (influence.controllingOwner().isEmpty()) {
            return CONTROL_NONE;
        }
        return influence.controllingOwner().get().equals(ownerId) ? CONTROL_OWN : CONTROL_FOREIGN;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}

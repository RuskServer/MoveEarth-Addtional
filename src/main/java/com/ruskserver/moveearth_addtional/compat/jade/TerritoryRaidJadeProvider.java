package com.ruskserver.moveearth_addtional.compat.jade;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.TerritoryRaidBlockEntity;
import com.ruskserver.moveearth_addtional.territory.raid.SableRaidLocator;
import com.ruskserver.moveearth_addtional.territory.raid.TerritoryRaidConfig;
import com.ruskserver.moveearth_addtional.territory.raid.TerritoryRaidService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

import java.util.Locale;

public enum TerritoryRaidJadeProvider implements IBlockComponentProvider,
        IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID,
            "territory_raid"
    );

    private static final String READY = "RaidReady";
    private static final String OWNER_NAME = "RaidOwnerName";
    private static final String ARMED = "RaidArmed";
    private static final String ON_SABLE_SHIP = "RaidOnSableShip";
    private static final String STATE = "RaidState";
    private static final String SPEED = "RaidSpeed";
    private static final String VALID_STRESS = "RaidValidStress";
    private static final String STRENGTH = "RaidStrength";
    private static final String RADIUS = "RaidRadius";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof TerritoryRaidBlockEntity raidBlock)) {
            return;
        }

        raidBlock.refreshRaidState();
        data.putBoolean(READY, true);
        data.putString(OWNER_NAME, raidBlock.getOwnerName());
        data.putBoolean(ARMED, raidBlock.isArmed());
        data.putBoolean(ON_SABLE_SHIP, SableRaidLocator.locate(raidBlock).isPresent());
        data.putString(STATE, raidBlock.getRuntimeState().name());
        data.putDouble(SPEED, Math.abs(raidBlock.getSpeed()));
        data.putDouble(VALID_STRESS, raidBlock.getValidStress());
        data.putDouble(STRENGTH, raidBlock.getSuppressionStrength());
        data.putDouble(RADIUS, TerritoryRaidConfig.RADIUS.get());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.getBoolean(READY)) {
            return;
        }

        tooltip.add(Component.translatable(
                "jade.moveearth_addtional.territory_raid.owner",
                data.getString(OWNER_NAME).isBlank() ? "?" : data.getString(OWNER_NAME)
        ));
        tooltip.add(Component.translatable(
                data.getBoolean(ARMED)
                        ? "jade.moveearth_addtional.territory_raid.armed"
                        : "jade.moveearth_addtional.territory_raid.disarmed"
        ));

        IThemeHelper theme = IThemeHelper.get();
        tooltip.add(data.getBoolean(ON_SABLE_SHIP)
                ? theme.success(Component.translatable("jade.moveearth_addtional.territory_raid.sable_ship"))
                : theme.warning(Component.translatable("jade.moveearth_addtional.territory_raid.not_sable_ship")));

        TerritoryRaidService.State state = readState(data.getString(STATE));
        Component stateLine = Component.translatable(stateKey(state));
        tooltip.add(switch (state) {
            case ACTIVE -> theme.success(stateLine);
            case DISARMED -> theme.info(stateLine);
            case NOT_ON_SABLE_SHIP, INSUFFICIENT_POWER -> theme.warning(stateLine);
            default -> theme.failure(stateLine);
        });
        tooltip.add(Component.translatable(
                "jade.moveearth_addtional.territory_raid.power",
                decimal(data.getDouble(SPEED)),
                decimal(data.getDouble(VALID_STRESS))
        ));
        tooltip.add(Component.translatable(
                "jade.moveearth_addtional.territory_raid.suppression",
                decimal(data.getDouble(STRENGTH)),
                decimal(data.getDouble(RADIUS))
        ));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    private static TerritoryRaidService.State readState(String name) {
        try {
            return TerritoryRaidService.State.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return TerritoryRaidService.State.INACTIVE;
        }
    }

    private static String stateKey(TerritoryRaidService.State state) {
        return "jade.moveearth_addtional.territory_raid.state."
                + state.name().toLowerCase(Locale.ROOT);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}

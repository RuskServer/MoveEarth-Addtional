package com.ruskserver.moveearth_addtional.compat.jade;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementEntry;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.api.ui.ScreenDirection;

@WailaPlugin
public final class ReinforcementJadePlugin implements IWailaPlugin {
    private static final ReinforcementProvider PROVIDER = new ReinforcementProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(PROVIDER, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(PROVIDER, Block.class);
    }

    private static final class ReinforcementProvider
            implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
                Moveearth_addtional.MODID, "reinforcement");
        private static final String DATA_RECEIVED = "MoveEarthReinforcementData";
        private static final String PRESENT = "MoveEarthReinforced";
        private static final String ENABLED = "MoveEarthReinforcementEnabled";
        private static final String CONSTRUCTING = "MoveEarthReinforcementConstructing";
        private static final String MATERIAL = "MoveEarthReinforcementMaterial";
        private static final String HP = "MoveEarthReinforcementHp";
        private static final String MAX_HP = "MoveEarthReinforcementMaxHp";
        private static final String ACTIVATION_TICKS = "MoveEarthReinforcementActivationTicks";

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getLevel() instanceof ServerLevel level)) return;
            data.putBoolean(DATA_RECEIVED, true);
            ReinforcementEntry entry = ReinforcementSavedData.get(level)
                    .get(accessor.getPosition()).orElse(null);
            data.putBoolean(PRESENT, entry != null);
            if (entry == null) return;
            data.putBoolean(ENABLED, entry.enabled());
            data.putBoolean(CONSTRUCTING, entry.activatesAt() > 0L);
            data.putString(MATERIAL, entry.material().id());
            data.putInt(HP, entry.durability());
            data.putInt(MAX_HP, entry.maxDurability());
            data.putInt(ACTIVATION_TICKS, (int) Math.min(Integer.MAX_VALUE,
                    entry.activationTicksRemaining(level.getGameTime())));
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.getBoolean(DATA_RECEIVED)) return;
            if (!data.getBoolean(PRESENT)) {
                tooltip.add(Component.translatable("jade.moveearth_addtional.reinforcement.none")
                        .withStyle(ChatFormatting.DARK_GRAY));
                return;
            }

            boolean enabled = data.getBoolean(ENABLED);
            boolean constructing = data.getBoolean(CONSTRUCTING);
            int hp = Math.max(0, data.getInt(HP));
            int maxHp = Math.max(1, data.getInt(MAX_HP));
            Component state;
            ChatFormatting color;
            int barStart;
            int barEnd;
            if (!enabled) {
                int seconds = Math.max(0, (data.getInt(ACTIVATION_TICKS) + 19) / 20);
                state = Component.translatable("jade.moveearth_addtional.reinforcement.curing", seconds);
                color = ChatFormatting.LIGHT_PURPLE;
                barStart = 0xFF8C4EB8;
                barEnd = 0xFFD58AFF;
            } else if (constructing) {
                state = Component.translatable("jade.moveearth_addtional.reinforcement.filling");
                color = ChatFormatting.GOLD;
                barStart = 0xFFC87A20;
                barEnd = 0xFFFFBE52;
            } else if (hp < maxHp) {
                state = Component.translatable("jade.moveearth_addtional.reinforcement.damaged");
                color = ChatFormatting.RED;
                barStart = 0xFFC9403A;
                barEnd = 0xFFFF7168;
            } else {
                state = Component.translatable("jade.moveearth_addtional.reinforcement.active");
                color = ChatFormatting.GREEN;
                barStart = 0xFF2FA866;
                barEnd = 0xFF6DE09B;
            }
            tooltip.add(Component.translatable("jade.moveearth_addtional.reinforcement.state", state)
                    .withStyle(color));
            tooltip.add(Component.translatable("jade.moveearth_addtional.reinforcement.material",
                    Component.translatable("jade.moveearth_addtional.reinforcement.material."
                            + data.getString(MATERIAL))).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("jade.moveearth_addtional.reinforcement.hp", hp, maxHp)
                    .withStyle(color));
            IElementHelper elements = IElementHelper.get();
            tooltip.add(elements.progress(Math.min(1.0F, hp / (float) maxHp), Component.empty(),
                    elements.progressStyle().color(barStart, barEnd).direction(ScreenDirection.RIGHT),
                    BoxStyle.getNestedBox(), false).size(new Vec2(110.0F, 5.0F)));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}

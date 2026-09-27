package com.ruskserver.moveearth_addtional.chat;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Builds recipient-specific sender decoration while leaving signed chat content untouched. */
public final class LocalChatPresentation {
    public static final ResourceKey<ChatType> CHAT_TYPE = ResourceKey.create(Registries.CHAT_TYPE,
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "local"));
    private static final int DISTANCE_COLOR = 0x8F9AA8;
    private static final int NATION_COLOR = 0x68E09B;
    private static final int ROLE_COLOR = 0xFFB454;
    private static final int NAME_COLOR = 0xDCE5EA;

    private static final String KEY = "chat.moveearth_addtional.local.";

    private LocalChatPresentation() { }

    private static MutableComponent bracketed(Component inner) {
        return Component.literal("[").append(inner).append("] ");
    }

    public static ChatType.Bound bound(ServerPlayer sender, ServerPlayer recipient) {
        NationSavedData nations = NationSavedData.get(sender.server);
        NationSavedData.Nation nation = nations.nationFor(sender.getUUID()).orElse(null);
        double distance = Math.sqrt(sender.distanceToSqr(recipient));
        LocalChatRules.Distance label = LocalChatRules.distance(distance, sender == recipient);
        Component distanceText = switch (label.kind()) {
            case SELF -> Component.translatable(KEY + "distance.self");
            case UNDER_ONE_BLOCK -> Component.translatable(KEY + "distance.under_one_block");
            case BLOCKS -> Component.translatable(KEY + "distance.blocks", label.blocks());
        };
        MutableComponent name = bracketed(distanceText).withColor(DISTANCE_COLOR);
        if (nation == null) {
            name.append(bracketed(Component.translatable(KEY + "unaffiliated")).withColor(NATION_COLOR));
        } else {
            String tag = nation.tag().isBlank() ? nation.name() : nation.tag();
            // The tag keeps chat lines short; hovering it shows the nation's full name.
            Component fullName = Component.translatable("chat.moveearth_addtional.local.nation_hover", nation.name());
            name.append(Component.literal("[" + tag + "] ").withStyle(style -> style.withColor(NATION_COLOR)
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, fullName))));
            NationSavedData.Member member = nation.members().get(sender.getUUID());
            NationSavedData.Role role = member == null ? null : nation.roles().get(member.roleId());
            if (role != null) {
                Component roleName = switch (role.id()) {
                    case NationSavedData.OWNER_ROLE -> Component.translatable(KEY + "role.owner");
                    case NationSavedData.MEMBER_ROLE -> Component.translatable(KEY + "role.member");
                    default -> Component.literal(role.displayName());
                };
                name.append(bracketed(roleName).withColor(ROLE_COLOR));
            }
        }
        name.append(Component.literal(sender.getGameProfile().getName()).withColor(NAME_COLOR));
        return ChatType.bind(CHAT_TYPE, sender.level().registryAccess(), name);
    }
}

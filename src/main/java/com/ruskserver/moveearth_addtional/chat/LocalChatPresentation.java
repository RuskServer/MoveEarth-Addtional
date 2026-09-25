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

    private LocalChatPresentation() { }

    public static ChatType.Bound bound(ServerPlayer sender, ServerPlayer recipient) {
        NationSavedData nations = NationSavedData.get(sender.server);
        NationSavedData.Nation nation = nations.nationFor(sender.getUUID()).orElse(null);
        double distance = Math.sqrt(sender.distanceToSqr(recipient));
        String distanceLabel = LocalChatRules.distanceLabel(distance, sender == recipient);
        MutableComponent name = Component.literal("[" + distanceLabel + "] ").withColor(DISTANCE_COLOR);
        if (nation == null) {
            name.append(Component.literal("[無所属] ").withColor(NATION_COLOR));
        } else {
            String tag = nation.tag().isBlank() ? nation.name() : nation.tag();
            name.append(Component.literal("[" + tag + "] ").withColor(NATION_COLOR));
            NationSavedData.Member member = nation.members().get(sender.getUUID());
            NationSavedData.Role role = member == null ? null : nation.roles().get(member.roleId());
            if (role != null) {
                String roleName = switch (role.id()) {
                    case NationSavedData.OWNER_ROLE -> "代表";
                    case NationSavedData.MEMBER_ROLE -> "国民";
                    default -> role.displayName();
                };
                name.append(Component.literal("[" + roleName + "] ").withColor(ROLE_COLOR));
            }
        }
        name.append(Component.literal(sender.getGameProfile().getName()).withColor(NAME_COLOR));
        return ChatType.bind(CHAT_TYPE, sender.level().registryAccess(), name);
    }
}

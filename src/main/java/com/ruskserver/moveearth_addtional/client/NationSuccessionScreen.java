package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_NationSettingsPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_S2ActionResultPacket;
import com.ruskserver.moveearth_addtional.s2.S2NationSnapshot;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;
import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class NationSuccessionScreen extends NationGovernanceScreen {
    private static int nextRequest;
    private final S2NationSnapshot snapshot;
    private int requestId;
    public NationSuccessionScreen(S2NationSnapshot snapshot) {
        super(Component.translatable("screen.moveearth_addtional.governance.succession_title"));
        this.snapshot = snapshot;
    }
    @Override protected List<Choice> choices() {
        return snapshot.members().stream().filter(m -> !"owner".equals(m.roleId()))
                .map(m -> new Choice(m.id(), Component.literal(m.name()), Component.literal(m.roleName()), true)).toList();
    }
    @Override protected Component detail() { return Component.translatable("screen.moveearth_addtional.governance.succession_detail"); }
    @Override protected Component heading() { return Component.translatable("screen.moveearth_addtional.nation.settings.owner", snapshot.ownerName()); }
    @Override protected Component confirmation() {
        String name = snapshot.members().stream().filter(m -> m.id().equals(selected)).map(S2NationSnapshot.MemberView::name).findFirst().orElse("");
        return Component.translatable("screen.moveearth_addtional.nation.settings.transfer_detail", name);
    }
    @Override protected void submit() {
        beginRequest(); requestId = ++nextRequest;
        PacketDistributor.sendToServer(new C2S_NationSettingsPacket(requestId, snapshot.revision(),
                C2S_NationSettingsPacket.Action.TRANSFER_OWNER, "", "", selected));
    }
    @Override protected void refresh() { onClose(); }
    public void handleResult(S2C_S2ActionResultPacket result) {
        if (result.requestId() == requestId) finished(Component.translatable(result.messageKey()), result.success() ? SUCCESS : DANGER);
    }
}

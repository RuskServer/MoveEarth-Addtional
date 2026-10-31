package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_CapitalRelocationPacket;
import com.ruskserver.moveearth_addtional.s2.territory.CapitalRelocationView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.List;
import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

public final class NationCapitalScreen extends NationGovernanceScreen {
    private static int nextRequest;
    private int requestId;
    private CapitalRelocationView view;
    public NationCapitalScreen() { super(Component.translatable("screen.moveearth_addtional.capital_relocation.title")); }
    @Override protected void initialLoad() { refresh(); }
    @Override protected List<Choice> choices() {
        if (view == null) return List.of();
        return view.cores().stream().filter(c -> !c.capital()).map(c -> new Choice(c.id(),
                Component.literal(location(c)), Component.translatable(c.reason()),
                c.reason().endsWith(".ready"))).toList();
    }
    @Override protected Component detail() { return Component.translatable("screen.moveearth_addtional.capital_relocation.detail"); }
    @Override protected Component heading() {
        String current = view == null ? "—" : view.cores().stream().filter(CapitalRelocationView.Core::capital)
                .findFirst().map(NationCapitalScreen::location).orElse("—");
        return Component.translatable("screen.moveearth_addtional.capital_relocation.current", current);
    }
    @Override protected boolean allowed() { return view != null && view.allowed(); }
    @Override protected Component confirmation() {
        String target = view.cores().stream().filter(c -> c.id().equals(selected)).findFirst().map(NationCapitalScreen::location).orElse("");
        return Component.translatable("screen.moveearth_addtional.capital_relocation.confirm", target,
                (view.cooldownTicks() + 1199) / 1200);
    }
    @Override protected void refresh() { request(false); }
    @Override protected void submit() { request(true); }
    private void request(boolean commit) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !NetworkRegistry.hasChannel(connection, C2S_CapitalRelocationPacket.TYPE.id())) {
            finished(Component.translatable("screen.moveearth_addtional.capital_relocation.unsupported"), DANGER); return;
        }
        beginRequest(); requestId = ++nextRequest;
        PacketDistributor.sendToServer(new C2S_CapitalRelocationPacket(requestId, view == null ? 0 : view.revision(), commit,
                view == null ? CapitalRelocationView.NONE : view.capitalId(), commit ? selected : CapitalRelocationView.NONE));
    }
    public static void receive(CapitalRelocationView view) {
        if (Minecraft.getInstance().screen instanceof NationCapitalScreen screen && screen.requestId == view.requestId()) {
            screen.view = view;
            if (view.cores().stream().noneMatch(c -> c.id().equals(screen.selected) && !c.capital())) screen.selected = null;
            screen.finished(Component.translatable(view.result(), Math.max(0L, (view.remainingTicks() + 1199) / 1200)),
                    view.result().endsWith(".success") ? SUCCESS : view.result().endsWith(".ready") ? MUTED : DANGER);
        }
    }
    private static String location(CapitalRelocationView.Core core) { return core.dimension() + " " + core.x() + ", " + core.y() + ", " + core.z(); }
}

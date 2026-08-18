package com.ruskserver.moveearth_addtional.client.ponder;

import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.block.TerritoryRaidBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TerritoryRaidScenes {
    private TerritoryRaidScenes() {
    }

    public static void usage(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("territory_raid", "移動式領土レイドコアの使い方");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9F);

        BlockPos raidPos = util.grid().at(1, 2, 2);
        BlockPos wheelPos = util.grid().at(5, 2, 2);
        Selection deck = util.select().fromTo(0, 1, 0, 5, 1, 4);
        Selection raidCore = util.select().position(raidPos);
        Selection driveTrain = util.select().fromTo(2, 2, 2, 5, 2, 2);
        Selection entireNetwork = util.select().fromTo(1, 2, 2, 5, 2, 2);

        BlockState raidState = ModBlocks.TERRITORY_RAID.get().defaultBlockState()
                .setValue(TerritoryRaidBlock.AXIS, Direction.Axis.X);
        BlockState shaftState = AllBlocks.SHAFT.getDefaultState()
                .setValue(ShaftBlock.AXIS, Direction.Axis.X);
        BlockState wheelState = AllBlocks.WATER_WHEEL.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST);

        scene.world().setBlocks(deck, Blocks.DARK_OAK_PLANKS.defaultBlockState(), false);
        scene.world().setBlocks(util.select().fromTo(0, 2, 2, 5, 2, 2), Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(raidPos, raidState, false);
        scene.world().setBlocks(util.select().fromTo(2, 2, 2, 4, 2, 2), shaftState, false);
        scene.world().setBlock(wheelPos, wheelState, false);
        scene.world().setKineticSpeed(entireNetwork, 0.0F);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(8);
        scene.world().showSection(deck, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("レイドコアはSableで組み立てた移動船の船上に設置します。固定地上設置では作動しません")
                .pointAt(util.vector().centerOf(raidPos.below()));
        scene.idle(90);

        scene.world().showSection(raidCore, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90)
                .text("設置者が所有者になり、領土メンバーも空手右クリックで武装・解除できます")
                .pointAt(util.vector().topOf(raidPos));
        scene.idle(80);

        scene.world().showSection(driveTrain, Direction.WEST);
        scene.idle(20);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("コアの軸方向へCreateの回転ネットワークを直結します")
                .pointAt(util.vector().blockSurface(raidPos, Direction.EAST));
        scene.idle(80);

        scene.overlay().showText(90)
                .colored(PonderPalette.RED)
                .text("16 RPM未満、過負荷、Creative Motorだけの動力では停止したままです")
                .pointAt(util.vector().centerOf(raidPos));
        scene.idle(80);

        scene.world().setKineticSpeed(entireNetwork, 16.0F);
        scene.effects().indicateSuccess(raidPos);
        scene.idle(20);
        scene.overlay().showText(110)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("16 RPMで1,024 SUを消費して起動します。投入応力が増えるほど領土減衰が強くなります")
                .pointAt(util.vector().topOf(raidPos));
        scene.idle(100);

        scene.overlay().showOutlineWithText(deck, 120)
                .text("既定半径は64ブロックです。船の移動と回転に減衰中心が追従します");
        scene.idle(110);

        scene.world().setKineticSpeed(entireNetwork, 0.0F);
        scene.effects().indicateRedstone(raidPos);
        scene.idle(15);
        scene.overlay().showText(120)
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .text("手動解除、動力停止、コア破壊、船やチャンクの消失、再起動時には減衰が解除されます")
                .pointAt(util.vector().centerOf(raidPos));
        scene.idle(110);
        scene.markAsFinished();
    }
}

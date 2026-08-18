package com.ruskserver.moveearth_addtional.client.ponder;

import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.block.TerritoryCoreBlock;
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
public final class TerritoryCoreScenes {
    private TerritoryCoreScenes() {
    }

    public static void usage(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("territory_core", "領土コアの使い方");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9F);

        BlockPos corePos = util.grid().at(1, 1, 2);
        BlockPos wheelPos = util.grid().at(5, 1, 2);
        Selection core = util.select().position(corePos);
        Selection driveTrain = util.select().fromTo(2, 1, 2, 5, 1, 2);
        Selection entireNetwork = util.select().fromTo(1, 1, 2, 5, 1, 2);

        BlockState coreState = ModBlocks.TERRITORY_CORE.get().defaultBlockState()
                .setValue(TerritoryCoreBlock.AXIS, Direction.Axis.X);
        BlockState shaftState = AllBlocks.SHAFT.getDefaultState()
                .setValue(ShaftBlock.AXIS, Direction.Axis.X);
        BlockState wheelState = AllBlocks.WATER_WHEEL.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST);

        scene.world().setBlocks(util.select().fromTo(0, 1, 2, 5, 1, 2), Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(corePos, coreState, false);
        scene.world().setBlocks(util.select().fromTo(2, 1, 2, 4, 1, 2), shaftState, false);
        scene.world().setBlock(wheelPos, wheelState, false);
        scene.world().setKineticSpeed(entireNetwork, 0.0F);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);
        scene.world().showSection(core, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("領土コアを設置したプレイヤーが所有者になります")
                .pointAt(util.vector().topOf(corePos));
        scene.idle(70);
        scene.overlay().showText(100)
                .text("プレイヤー検知ブロックのホワイトリストが、そのまま領土メンバーとして使われます")
                .pointAt(util.vector().centerOf(corePos));
        scene.idle(90);

        scene.world().showSection(driveTrain, Direction.WEST);
        scene.idle(20);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("コアの軸方向へ、Createの回転ネットワークを直接接続できます")
                .pointAt(util.vector().blockSurface(corePos, Direction.EAST));
        scene.idle(70);

        scene.world().setKineticSpeed(entireNetwork, 16.0F);
        scene.effects().indicateSuccess(corePos);
        scene.idle(20);
        scene.overlay().showText(110)
                .colored(PonderPalette.GREEN)
                .text("正常稼働中はコア自身が消費した応力を工業力へ変換します。既定値では16 RPMで512 SUです")
                .pointAt(util.vector().topOf(corePos));
        scene.idle(100);

        scene.world().setKineticSpeed(entireNetwork, 0.0F);
        scene.idle(15);
        scene.overlay().showText(100)
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .text("無回転・過負荷停止・クリエイティブ動力は、コアへの有効な応力供給になりません")
                .pointAt(util.vector().centerOf(corePos));
        scene.idle(90);

        scene.world().setKineticSpeed(entireNetwork, 64.0F);
        scene.effects().indicateSuccess(corePos);
        scene.idle(15);
        scene.overlay().showOutlineWithText(core, 120)
                .attachKeyFrame()
                .text("直接接続とは別に、コア周辺128ブロックの稼働中工場も評価されます。同じ応力は二重計上されません");
        scene.idle(110);
        scene.markAsFinished();
    }
}

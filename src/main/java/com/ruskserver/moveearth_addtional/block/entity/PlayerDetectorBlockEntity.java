package com.ruskserver.moveearth_addtional.block.entity;

import com.ruskserver.moveearth_addtional.data.PlayerWhitelistSavedData;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.BankAPI;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValueParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PlayerDetectorBlockEntity extends BlockEntity {

    public static final double DETECTION_RANGE = 100.0D;
    public static final long WARMUP_DURATION_MS = 20 * 60 * 1000L;

    private static final String DUMMY_TAG = "MoveEarthDetectorDummy";
    private static final String DUMMY_BLOCK_POS_TAG = "MoveEarthDetectorBlockPos";
    private static final String DUMMY_DIMENSION_TAG = "MoveEarthDetectorDimension";
    private static final double DUMMY_POSITION_EPSILON_SQR = 0.0001D;

    private UUID ownerUUID;
    private String ownerName;
    private int tickCounter = 0;

    // 維持費支払い用データ
    private BankReference bankReference = null;
    private long nextPaymentTime = 0L;
    private boolean isActive = false;

    // 作動猶予・ダミーエンティティ用データ
    private long placedTime = 0L;
    private UUID dummyEntityUUID = null;

    // リフレクションによる protected な DATA_SHARED_FLAGS_ID 取得
    private static net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_SHARED_FLAGS = null;
    static {
        try {
            java.lang.reflect.Field field = net.minecraft.world.entity.Entity.class.getDeclaredField("DATA_SHARED_FLAGS_ID");
            field.setAccessible(true);
            DATA_SHARED_FLAGS = (net.minecraft.network.syncher.EntityDataAccessor<Byte>) field.get(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public PlayerDetectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLAYER_DETECTOR.get(), pos, state);
    }

    public void setOwner(UUID ownerUUID, String ownerName) {
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.placedTime = System.currentTimeMillis(); // 設置時刻を設定
        this.setChanged();
    }

    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public BankReference getBankReference() {
        return this.bankReference;
    }

    public void setBankReference(BankReference bankReference) {
        this.bankReference = bankReference;
        this.setChanged();
    }

    public long getNextPaymentTime() {
        return this.nextPaymentTime;
    }

    public void setNextPaymentTime(long nextPaymentTime) {
        this.nextPaymentTime = nextPaymentTime;
        this.setChanged();
    }

    public boolean isActive() {
        return this.isActive;
    }

    public boolean isOperational(long currentTimeMillis) {
        return this.isActive
                && this.ownerUUID != null
                && this.placedTime > 0L
                && this.nextPaymentTime > currentTimeMillis
                && currentTimeMillis >= this.placedTime + WARMUP_DURATION_MS;
    }

    public void setActive(boolean active) {
        this.isActive = active;
        this.setChanged();
    }

    public long getPlacedTime() {
        return this.placedTime;
    }

    public void setPlacedTime(long placedTime) {
        this.placedTime = placedTime;
        this.setChanged();
    }

    public UUID getDummyEntityUUID() {
        return this.dummyEntityUUID;
    }

    public void setDummyEntityUUID(UUID dummyEntityUUID) {
        this.dummyEntityUUID = dummyEntityUUID;
        this.setChanged();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("OwnerUUID")) {
            this.ownerUUID = tag.getUUID("OwnerUUID");
        }
        if (tag.contains("OwnerName")) {
            this.ownerName = tag.getString("OwnerName");
        }
        if (tag.contains("BankReference")) {
            this.bankReference = BankReference.load(tag.getCompound("BankReference"));
        }
        if (tag.contains("NextPaymentTime")) {
            this.nextPaymentTime = tag.getLong("NextPaymentTime");
        }
        if (tag.contains("IsActive")) {
            this.isActive = tag.getBoolean("IsActive");
        }
        if (tag.contains("PlacedTime")) {
            this.placedTime = tag.getLong("PlacedTime");
        }
        if (tag.hasUUID("DummyEntityUUID")) {
            this.dummyEntityUUID = tag.getUUID("DummyEntityUUID");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.ownerUUID != null) {
            tag.putUUID("OwnerUUID", this.ownerUUID);
        }
        if (this.ownerName != null) {
            tag.putString("OwnerName", this.ownerName);
        }
        if (this.bankReference != null) {
            tag.put("BankReference", this.bankReference.save());
        }
        tag.putLong("NextPaymentTime", this.nextPaymentTime);
        tag.putBoolean("IsActive", this.isActive);
        tag.putLong("PlacedTime", this.placedTime);
        if (this.dummyEntityUUID != null) {
            tag.putUUID("DummyEntityUUID", this.dummyEntityUUID);
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state, PlayerDetectorBlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;

        // ダミーエンティティ（シュルカー）の同期管理
        blockEntity.maintainDummyEntity(serverLevel, pos);

        blockEntity.tickCounter++;
        if (blockEntity.tickCounter >= 100) { // 5秒周期 (100 ticks)
            blockEntity.tickCounter = 0;

            if (blockEntity.ownerUUID == null) {
                return;
            }

            long currentTime = System.currentTimeMillis();

            // 設置時刻のフォールバック初期化
            if (blockEntity.placedTime == 0L) {
                blockEntity.placedTime = currentTime;
                blockEntity.setChanged();
            }

            // 維持費支払い期限のチェックと自動引き落とし
            if (blockEntity.isActive && currentTime >= blockEntity.nextPaymentTime) {
                boolean paySuccess = false;
                if (blockEntity.bankReference != null && blockEntity.bankReference.isValid()) {
                    IBankAccount account = blockEntity.bankReference.get();
                    if (account != null) {
                        MoneyValue fee = MoneyValueParser.ParseConfigString("coin;5-lightmanscurrency:coin_gold", () -> MoneyValue.empty());
                        if (account.getStoredMoney().containsValue(fee)) {
                            var result = BankAPI.getApi().BankWithdrawFromServer(account, fee);
                            if (result.getFirst()) {
                                paySuccess = true;
                                // 2時間の延長 (7,200,000 ミリ秒)
                                blockEntity.nextPaymentTime = currentTime + (2 * 60 * 60 * 1000);
                                blockEntity.setChanged();
                            }
                        }
                    }
                }

                if (!paySuccess) {
                    // 支払い失敗時の停止処理
                    blockEntity.isActive = false;
                    blockEntity.setChanged();
                    notifyOwnerOfPaymentFailure(serverLevel, blockEntity);
                }
            }

            // 非アクティブな場合は、検知処理をスキップ
            if (!blockEntity.isActive) {
                return;
            }

            // 設置から20分間は作動しない（ウォーミングアップ猶予時間）
            if (currentTime < blockEntity.placedTime + WARMUP_DURATION_MS) {
                return;
            }

            // 所有者の最新ホワイトリストデータを取得
            PlayerWhitelistSavedData whitelistData = PlayerWhitelistSavedData.get(serverLevel);

            // 周囲100ブロック以内のプレイヤーをスキャン
            double range = DETECTION_RANGE;
            AABB aabb = new AABB(pos).inflate(range);
            List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, aabb);

            for (ServerPlayer player : players) {
                double dist = player.position().distanceTo(Vec3.atCenterOf(pos));
                if (dist <= range) {
                    String targetName = player.getScoreboardName();

                    // 所有者およびホワイトリストに入っているプレイヤーは除外
                    if (player.getUUID().equals(blockEntity.ownerUUID) || whitelistData.isWhitelisted(blockEntity.ownerUUID, player.getUUID())) {
                        blockEntity.sendGlowingPacket(player, false); // 発光を解除
                        continue;
                    }

                    // 侵入者を検知
                    // 1. 警告メッセージの構築と送信
                    String warningMsg = String.format("【警告】侵入者を検知しました！ プレイヤー: %s (距離: %.1fm)", targetName, dist);
                    Component chatMessage = Component.literal(warningMsg);

                    Set<String> alertRecipients = new HashSet<>(whitelistData.getMemberNamesForDisplay(blockEntity.ownerUUID));
                    if (blockEntity.ownerName != null) {
                        alertRecipients.add(blockEntity.ownerName);
                    }

                    for (ServerPlayer onlinePlayer : serverLevel.getServer().getPlayerList().getPlayers()) {
                        if (alertRecipients.contains(onlinePlayer.getScoreboardName())) {
                            onlinePlayer.sendSystemMessage(chatMessage);
                        }
                    }

                    // 2. 30ブロック以内の場合、侵入者に発光を付与し、さらにダミー発光をそのプレイヤーにのみ送信
                    if (dist <= 30.0) {
                        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false));
                        blockEntity.sendGlowingPacket(player, true);
                    } else {
                        blockEntity.sendGlowingPacket(player, false);
                    }
                } else {
                    blockEntity.sendGlowingPacket(player, false);
                }
            }
        }
    }

    // --- ダミーエンティティ管理・個別発光パケット処理ヘルパー ---

    public static boolean isDetectorDummy(Entity entity) {
        return entity instanceof Shulker
                && entity.getPersistentData().getBoolean(DUMMY_TAG);
    }

    /**
     * Keeps the detector's visual dummy attached to its owning block. This is
     * intentionally also called at the end of the server tick so movement made
     * by other mods is undone before the next client update.
     */
    public void maintainDummyEntity(ServerLevel level, BlockPos pos) {
        if (!this.isActive) {
            if (this.dummyEntityUUID != null) {
                removeDummyEntity(level);
            }
            return;
        }

        Entity entity = this.dummyEntityUUID == null ? null : findDummyEntity(level);
        if (!(entity instanceof Shulker shulker) || !isDetectorDummy(shulker) || !shulker.isAlive()) {
            spawnDummyEntity(level, pos);
            return;
        }

        if (shulker.level() != level) {
            shulker.discard();
            spawnDummyEntity(level, pos);
            return;
        }

        pinDummyEntity(level, pos, shulker);
    }

    /**
     * Validates a detector dummy when an entity is loaded or released by a mob
     * transport/capture mod. A stale duplicate must not survive independently.
     */
    public static void validateLoadedDummy(ServerLevel level, Shulker shulker) {
        if (!isDetectorDummy(shulker)) {
            return;
        }

        CompoundTag data = shulker.getPersistentData();
        if (!data.contains(DUMMY_BLOCK_POS_TAG) || !data.contains(DUMMY_DIMENSION_TAG)) {
            // Adopt a legitimate legacy dummy in place, but remove legacy copies
            // that had already been carried away before this protection existed.
            BlockPos legacyOwnerPos = BlockPos.containing(shulker.position());
            if (level.hasChunkAt(legacyOwnerPos)
                    && level.getBlockEntity(legacyOwnerPos) instanceof PlayerDetectorBlockEntity detector
                    && detector.isActive
                    && shulker.getUUID().equals(detector.dummyEntityUUID)) {
                detector.pinDummyEntity(level, legacyOwnerPos, shulker);
            } else {
                shulker.discard();
            }
            return;
        }

        String expectedDimension = data.getString(DUMMY_DIMENSION_TAG);
        if (!level.dimension().location().toString().equals(expectedDimension)) {
            shulker.discard();
            return;
        }

        BlockPos ownerPos = BlockPos.of(data.getLong(DUMMY_BLOCK_POS_TAG));
        if (!level.hasChunkAt(ownerPos)) {
            // The owner is not loaded while this entity is: it was moved away.
            shulker.discard();
            return;
        }

        if (!(level.getBlockEntity(ownerPos) instanceof PlayerDetectorBlockEntity detector)
                || !detector.isActive
                || detector.dummyEntityUUID == null
                || !detector.dummyEntityUUID.equals(shulker.getUUID())) {
            shulker.discard();
            return;
        }

        detector.pinDummyEntity(level, ownerPos, shulker);
    }

    private Entity findDummyEntity(ServerLevel currentLevel) {
        for (ServerLevel candidate : currentLevel.getServer().getAllLevels()) {
            Entity entity = candidate.getEntity(this.dummyEntityUUID);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private void pinDummyEntity(ServerLevel level, BlockPos pos, Shulker shulker) {
        double expectedX = pos.getX() + 0.5D;
        double expectedY = pos.getY();
        double expectedZ = pos.getZ() + 0.5D;

        shulker.setNoAi(true);
        shulker.setNoGravity(true);
        shulker.setInvisible(true);
        shulker.setSilent(true);
        shulker.setInvulnerable(true);
        shulker.setPersistenceRequired();
        shulker.noPhysics = true;
        shulker.setDeltaMovement(Vec3.ZERO);
        if (shulker.isPassenger()) {
            shulker.stopRiding();
        }

        if (shulker.distanceToSqr(expectedX, expectedY, expectedZ) > DUMMY_POSITION_EPSILON_SQR) {
            shulker.moveTo(expectedX, expectedY, expectedZ, 0.0F, 0.0F);
        }

        CompoundTag data = shulker.getPersistentData();
        data.putBoolean(DUMMY_TAG, true);
        data.putLong(DUMMY_BLOCK_POS_TAG, pos.asLong());
        data.putString(DUMMY_DIMENSION_TAG, level.dimension().location().toString());
    }

    private void spawnDummyEntity(ServerLevel level, BlockPos pos) {
        // 二重生成を防ぐため既存のものをクリア
        removeDummyEntity(level);

        Shulker shulker = EntityType.SHULKER.create(level);
        if (shulker != null) {
            shulker.getPersistentData().putBoolean(DUMMY_TAG, true);
            pinDummyEntity(level, pos, shulker);

            this.dummyEntityUUID = shulker.getUUID();
            this.setChanged();
            if (!level.addFreshEntity(shulker)) {
                this.dummyEntityUUID = null;
                this.setChanged();
            }
        }
    }

    private void removeDummyEntity(ServerLevel level) {
        if (this.dummyEntityUUID != null) {
            net.minecraft.world.entity.Entity entity = level.getEntity(this.dummyEntityUUID);
            if (entity != null) {
                entity.discard();
            }
            this.dummyEntityUUID = null;
            this.setChanged();
        }
        // 座標周囲の残留シュルカーを念のため全クリーンアップ（座標基準）
        AABB searchBox = new AABB(this.worldPosition).inflate(1.5);
        List<Shulker> shulkers = level.getEntitiesOfClass(Shulker.class, searchBox);
        for (Shulker shulker : shulkers) {
            CompoundTag data = shulker.getPersistentData();
            boolean belongsToThisDetector = data.contains(DUMMY_BLOCK_POS_TAG)
                    ? BlockPos.of(data.getLong(DUMMY_BLOCK_POS_TAG)).equals(this.worldPosition)
                    : shulker.distanceToSqr(
                            this.worldPosition.getX() + 0.5D,
                            this.worldPosition.getY(),
                            this.worldPosition.getZ() + 0.5D) <= 0.25D;
            if (data.getBoolean(DUMMY_TAG) && belongsToThisDetector) {
                shulker.discard();
            }
        }
    }

    public void onDestroy(ServerLevel level) {
        removeDummyEntity(level);
    }

    private void sendGlowingPacket(ServerPlayer player, boolean isGlowing) {
        if (this.dummyEntityUUID != null && player.level() instanceof ServerLevel serverLevel && DATA_SHARED_FLAGS != null) {
            net.minecraft.world.entity.Entity entity = serverLevel.getEntity(this.dummyEntityUUID);
            if (entity != null) {
                byte flags = entity.getEntityData().get(DATA_SHARED_FLAGS);
                byte newFlags;
                if (isGlowing) {
                    newFlags = (byte) (flags | (1 << 6)); // GLOWINGビットをON
                } else {
                    newFlags = (byte) (flags & ~(1 << 6)); // GLOWINGビットをOFF
                }

                List<SynchedEntityData.DataValue<?>> packedData = new ArrayList<>();
                packedData.add(SynchedEntityData.DataValue.create(DATA_SHARED_FLAGS, newFlags));

                player.connection.send(new ClientboundSetEntityDataPacket(entity.getId(), packedData));
            }
        }
    }

    private void notifyOwnerOfPaymentFailure(ServerLevel level, PlayerDetectorBlockEntity blockEntity) {
        if (blockEntity.ownerUUID != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(blockEntity.ownerUUID);
            if (player != null) {
                player.sendSystemMessage(Component.literal("§c【警告】プレイヤー検知ブロックの維持費（5ゴールド）の引き落としに失敗したため、機能が停止しました。GUIから口座残高の確認または支払い口座の再設定を行ってください。"));
            }
        }
    }
}

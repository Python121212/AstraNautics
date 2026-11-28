package com.astranautics.entity;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicsSwitchBlock;
import com.astranautics.config.AstraConfig;
import com.astranautics.physics.ContactCache;
import com.astranautics.physics.EntityShipCollision;
import com.astranautics.physics.PhysicsParams;
import com.astranautics.physics.RigidBody;
import com.astranautics.physics.ShipPhysics;
import com.astranautics.physics.ShipStructure;
import com.astranautics.physics.ShipStructureCodec;
import com.astranautics.physics.ShipWorld;
import com.astranautics.physics.VoxelMining;
import com.astranautics.physics.VoxelSpatialIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A block structure that has been detached from the world and is simulated as a
 * single rigid body.
 *
 * <p>The entity position is the centre of mass; the voxel grid is kept in local
 * coordinates and rendered with the synced orientation.</p>
 */
public class PhysicsShip extends Entity implements IEntityAdditionalSpawnData {
    private static final EntityDataAccessor<String> DATA_STRUCTURE =
            SynchedEntityData.defineId(PhysicsShip.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_QX =
            SynchedEntityData.defineId(PhysicsShip.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QY =
            SynchedEntityData.defineId(PhysicsShip.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QZ =
            SynchedEntityData.defineId(PhysicsShip.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QW =
            SynchedEntityData.defineId(PhysicsShip.class, EntityDataSerializers.FLOAT);

    private final RigidBody body = new RigidBody();
    private final ContactCache contactCache = new ContactCache();
    private ShipStructure structure;
    private boolean structureSyncPending;

    /** Last punch of a player, so a click that becomes a hold can be taken back. */
    @Nullable
    private UUID lastPunchPlayer;
    private long lastPunchTick = Long.MIN_VALUE;
    private final Vector3d lastPunchImpulse = new Vector3d();
    private final Vector3d lastPunchPoint = new Vector3d();
    /** Saved block entity data per local voxel (chest contents, sign text, ...). */
    private final Map<BlockPos, CompoundTag> blockEntities = new LinkedHashMap<>();
    /** Remaining ticks during which the structure is (re)sent, so late tracking still receives it. */
    private int structureSyncTicks;
    /**
     * Half extents of the world-space bounding box, derived from the structure and rotation.
     * Created lazily: {@code Entity}'s constructor calls {@code setPos -> makeBoundingBox}
     * before subclass field initialisers run, so a plain initialiser would be null there.
     */
    @Nullable
    private Vector3d halfExtents;
    @Nullable
    private VoxelSpatialIndex spatialIndex;

    // Sleeping (perf): a settled ship skips the solver until something wakes it.
    private boolean sleeping;
    private int sleepTicks;

    // Client-side interpolation of the orientation
    private final Quaterniond currentClientOrientation = new Quaterniond();
    private final Quaterniond previousClientOrientation = new Quaterniond();
    private boolean clientOrientationKnown;

    public PhysicsShip(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        if (!level.isClientSide) {
            ShipWorld.add(this);
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STRUCTURE, "");
        this.entityData.define(DATA_QX, 0.0F);
        this.entityData.define(DATA_QY, 0.0F);
        this.entityData.define(DATA_QZ, 0.0F);
        this.entityData.define(DATA_QW, 1.0F);
    }

    /** Server-side: remember the block entity contents that travel with the hull. */
    public void setBlockEntityData(Map<BlockPos, CompoundTag> data) {
        this.blockEntities.clear();
        this.blockEntities.putAll(data);
    }

    /** Server-side: turn the structure into a dynamic rigid body at {@code worldCenterOfMass}. */
    public void initialise(ShipStructure structure, Vector3d worldCenterOfMass) {
        this.structure = structure;
        this.body.setMassProperties(structure.getMass(), structure.getInertiaLocal());
        this.body.position.set(worldCenterOfMass);
        this.body.orientation.identity();
        this.body.updateRotationMatrix();
        this.body.setKinematic(false);
        this.setPos(worldCenterOfMass.x, worldCenterOfMass.y, worldCenterOfMass.z);
        this.structureSyncPending = true;
        this.structureSyncTicks = 20;
        this.spatialIndex = null;
        this.wake();
        this.pushOrientation();
        this.updateBoundingExtents();
    }

    @Nullable
    public ShipStructure getStructure() {
        if (this.structure == null && this.level().isClientSide) {
            this.structure = ShipStructureCodec.decode(this.entityData.get(DATA_STRUCTURE));
        }
        return this.structure;
    }

    public RigidBody getBody() {
        return body;
    }

    public boolean isSleeping() {
        return sleeping;
    }

    public void wake() {
        this.sleeping = false;
        this.sleepTicks = 0;
    }

    /** Never null, even while the super constructor is still running. */
    private Vector3d extents() {
        Vector3d current = this.halfExtents;
        if (current == null) {
            current = new Vector3d(0.5D, 0.5D, 0.5D);
            this.halfExtents = current;
        }
        return current;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        Vector3d half = extents();
        float width = (float) Math.max(0.5D, Math.max(half.x, half.z) * 2.0D);
        float height = (float) Math.max(0.5D, half.y * 2.0D);
        return EntityDimensions.scalable(width, height);
    }

    @Override
    protected AABB makeBoundingBox() {
        Vector3d half = extents();
        return new AABB(getX() - half.x, getY() - half.y, getZ() - half.z,
                getX() + half.x, getY() + half.y, getZ() + half.z);
    }

    /**
     * Recomputes the axis-aligned world extents of the rotated structure so that
     * culling, entity collisions and the ship-vs-ship broad phase all see the real
     * size of the ship instead of a 1x1x1 box.
     */
    private void updateBoundingExtents() {
        Vector3d half = extents();
        ShipStructure current = this.structure;
        if (current == null) {
            half.set(0.5D, 0.5D, 0.5D);
            return;
        }

        AABB local = current.getLocalBounds();
        Vector3d com = current.getCenterOfMass();
        double hx = Math.max(Math.abs(local.minX - com.x), Math.abs(local.maxX - com.x));
        double hy = Math.max(Math.abs(local.minY - com.y), Math.abs(local.maxY - com.y));
        double hz = Math.max(Math.abs(local.minZ - com.z), Math.abs(local.maxZ - com.z));

        Matrix3d rotation = this.body.getRotationMatrix();
        half.set(
                Math.abs(rotation.m00) * hx + Math.abs(rotation.m01) * hy + Math.abs(rotation.m02) * hz,
                Math.abs(rotation.m10) * hx + Math.abs(rotation.m11) * hy + Math.abs(rotation.m12) * hz,
                Math.abs(rotation.m20) * hx + Math.abs(rotation.m21) * hy + Math.abs(rotation.m22) * hz);
        this.setBoundingBox(makeBoundingBox());
    }

    // The structure is part of the spawn packet, so a player who starts tracking an
    // already assembled ship still sees its blocks (previously the ship was invisible
    // until the entity was recreated).
    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.structure == null ? "" : ShipStructureCodec.encode(this.structure), 1 << 21);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        String data = buffer.readUtf(1 << 21);
        if (!data.isEmpty()) {
            this.structure = ShipStructureCodec.decode(data);
            this.spatialIndex = null;
            this.updateBoundingExtents();
        }
    }

    /** Rebuilds the per-tick broad-phase index used by collisions and rendering. */
    public void refreshSpatialIndex() {
        ShipStructure current = getStructure();
        this.spatialIndex = current == null ? null : VoxelSpatialIndex.build(this.body, current);
    }

    @Nullable
    public VoxelSpatialIndex getSpatialIndex() {
        if (this.spatialIndex == null) {
            refreshSpatialIndex();
        }
        return this.spatialIndex;
    }

    public Quaterniond getRenderOrientation(float partialTick) {
        if (!clientOrientationKnown) {
            return new Quaterniond(readSyncedOrientation());
        }
        return new Quaterniond(previousClientOrientation)
                .slerp(currentClientOrientation, Math.max(0.0F, Math.min(1.0F, partialTick)));
    }

    private Quaterniond readSyncedOrientation() {
        return new Quaterniond(
                this.entityData.get(DATA_QX),
                this.entityData.get(DATA_QY),
                this.entityData.get(DATA_QZ),
                this.entityData.get(DATA_QW));
    }

    private void pushOrientation() {
        this.entityData.set(DATA_QX, (float) body.orientation.x);
        this.entityData.set(DATA_QY, (float) body.orientation.y);
        this.entityData.set(DATA_QZ, (float) body.orientation.z);
        this.entityData.set(DATA_QW, (float) body.orientation.w);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (this.level().isClientSide && DATA_STRUCTURE.equals(key)) {
            this.structure = ShipStructureCodec.decode(this.entityData.get(DATA_STRUCTURE));
            this.spatialIndex = null;
            this.updateBoundingExtents();
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            this.previousClientOrientation.set(this.currentClientOrientation);
            this.currentClientOrientation.set(readSyncedOrientation());
            this.clientOrientationKnown = true;

            // Mirror the synced pose into the local body so the bounding box, the voxel
            // index and the client-side entity collision all use the real transform.
            this.body.position.set(this.getX(), this.getY(), this.getZ());
            this.body.orientation.set(this.currentClientOrientation);
            this.body.updateRotationMatrix();
            this.updateBoundingExtents();

            if (AstraConfig.ENABLE_ENTITY_COLLISIONS.get()) {
                this.spatialIndex = null;
                VoxelSpatialIndex index = this.getSpatialIndex();
                if (index != null) {
                    // Resolve locally as well: the local player is simulated by its own
                    // client, so this is what makes the hull feel like solid blocks
                    // instead of being yanked around by server corrections.
                    EntityShipCollision.resolve(this.level(), this, index);
                }
            }
            return;
        }

        this.tickServer((ServerLevel) this.level());
    }

    private void tickServer(ServerLevel level) {
        try {
            this.tickServerUnsafe(level);
        } catch (Exception exception) {
            // A physics bug must never take the whole world down: park the ship by
            // putting its blocks back and removing the entity.
            AstraNautics.LOGGER.error("[astranautics] physics tick failed for the ship at {}, dissolving it",
                    this.blockPosition(), exception);
            try {
                this.dissolve(level);
            } catch (Exception nested) {
                AstraNautics.LOGGER.error("[astranautics] dissolving the broken ship failed too", nested);
                this.discard();
            }
        }
    }

    private void tickServerUnsafe(ServerLevel level) {
        if (this.structure == null) {
            return;
        }

        if (this.structureSyncPending || this.structureSyncTicks > 0) {
            this.entityData.set(DATA_STRUCTURE, ShipStructureCodec.encode(this.structure));
            this.structureSyncPending = false;
            if (this.structureSyncTicks > 0) {
                this.structureSyncTicks--;
            }
        }

        if (this.sleeping) {
            return;
        }

        PhysicsParams params = PhysicsParams.fromConfig();

        // Adaptive substepping: fast ships get more substeps so they cannot tunnel
        // through the world between two collision checks.
        double speed = Math.max(this.body.linearVelocity.length(),
                this.body.angularVelocity.length() * 4.0D);
        int substeps = Math.max(AstraConfig.SUBSTEPS.get(),
                Math.min(16, (int) Math.ceil(speed * (1.0D / 20.0D) / 0.4D)));
        double h = (1.0D / 20.0D) / substeps;

        for (int i = 0; i < substeps; i++) {
            ShipPhysics.step(level, this.body, this.structure, h, params, this.contactCache);
        }

        this.setPos(body.position.x, body.position.y, body.position.z);
        this.pushOrientation();
        this.updateBoundingExtents();
        this.spatialIndex = null;
        this.updateSleepState();

        if (body.position.y < level.getMinBuildHeight() - 48.0D) {
            dissolve(level);
        }
    }

    private void updateSleepState() {
        double linear = this.body.linearVelocity.length();
        double angular = this.body.angularVelocity.length();
        boolean slow = linear < AstraConfig.SLEEP_LINEAR_THRESHOLD.get()
                && angular < AstraConfig.SLEEP_ANGULAR_THRESHOLD.get();

        if (!slow) {
            this.sleepTicks = 0;
            return;
        }

        this.sleepTicks++;
        if (this.sleepTicks >= AstraConfig.SLEEP_TICKS.get()) {
            this.sleeping = true;
            this.body.linearVelocity.zero();
            this.body.angularVelocity.zero();
            this.contactCache.clear();
        }
    }

    /** Puts every block back into the world and removes the entity. */
    public void dissolve(ServerLevel level) {
        if (this.structure != null) {
            Matrix3d rotation = new Matrix3d(this.body.getRotationMatrix());
            Vector3d local = new Vector3d();
            Vector3d world = new Vector3d();

            for (Map.Entry<BlockPos, BlockState> entry : this.structure.voxels().entrySet()) {
                this.structure.localCenterRelativeToCom(entry.getKey(), local);
                rotation.transform(local, world);
                world.add(this.body.position);

                BlockPos target = BlockPos.containing(world.x, world.y, world.z);
                BlockState state = entry.getValue();
                CompoundTag storedData = this.blockEntities.get(entry.getKey());
                if (state.getBlock() instanceof PhysicsSwitchBlock) {
                    // The hull carried the switch in its ON position: put it back OFF so it
                    // is ready to be switched on again.
                    state = state.setValue(PhysicsSwitchBlock.ENABLED, false);
                }
                if (level.isEmptyBlock(target) || level.getBlockState(target).canBeReplaced()) {
                    level.setBlock(target, state, 3);
                    if (storedData != null) {
                        BlockEntity restored = level.getBlockEntity(target);
                        if (restored != null) {
                            restored.load(storedData.copy());
                            restored.setChanged();
                        }
                    }
                } else {
                    Block.popResource(level, target, new ItemStack(state.getBlock()));
                }
            }
            this.structure = null;
        }
        this.discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        ShipWorld.remove(this);
        this.contactCache.clear();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Structure", Tag.TAG_STRING)) {
            ShipStructure loaded = ShipStructureCodec.decode(tag.getString("Structure"));
            if (loaded != null) {
                this.structure = loaded;
                this.body.setMassProperties(loaded.getMass(), loaded.getInertiaLocal());
            }
        }

        if (tag.contains("Body", Tag.TAG_COMPOUND)) {
            CompoundTag bodyTag = tag.getCompound("Body");
            this.body.position.set(bodyTag.getDouble("X"), bodyTag.getDouble("Y"), bodyTag.getDouble("Z"));
            this.body.orientation.set(bodyTag.getDouble("QX"), bodyTag.getDouble("QY"),
                    bodyTag.getDouble("QZ"), bodyTag.getDouble("QW")).normalize();
            this.body.linearVelocity.set(bodyTag.getDouble("VX"), bodyTag.getDouble("VY"),
                    bodyTag.getDouble("VZ"));
            this.body.angularVelocity.set(bodyTag.getDouble("WX"), bodyTag.getDouble("WY"),
                    bodyTag.getDouble("WZ"));
            this.body.updateRotationMatrix();
            this.setPos(body.position.x, body.position.y, body.position.z);
        }

        this.blockEntities.clear();
        if (tag.contains("BlockEntities", Tag.TAG_LIST)) {
            ListTag list = tag.getList("BlockEntities", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag voxelTag = list.getCompound(i);
                BlockPos pos = new BlockPos(voxelTag.getInt("X"), voxelTag.getInt("Y"), voxelTag.getInt("Z"));
                this.blockEntities.put(pos, voxelTag.getCompound("Data").copy());
            }
        }

        this.structureSyncTicks = 20;
        this.updateBoundingExtents();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.structure != null) {
            tag.putString("Structure", ShipStructureCodec.encode(this.structure));
        }

        CompoundTag bodyTag = new CompoundTag();
        bodyTag.putDouble("X", body.position.x);
        bodyTag.putDouble("Y", body.position.y);
        bodyTag.putDouble("Z", body.position.z);
        bodyTag.putDouble("QX", body.orientation.x);
        bodyTag.putDouble("QY", body.orientation.y);
        bodyTag.putDouble("QZ", body.orientation.z);
        bodyTag.putDouble("QW", body.orientation.w);
        bodyTag.putDouble("VX", body.linearVelocity.x);
        bodyTag.putDouble("VY", body.linearVelocity.y);
        bodyTag.putDouble("VZ", body.linearVelocity.z);
        bodyTag.putDouble("WX", body.angularVelocity.x);
        bodyTag.putDouble("WY", body.angularVelocity.y);
        bodyTag.putDouble("WZ", body.angularVelocity.z);
        tag.put("Body", bodyTag);

        if (!this.blockEntities.isEmpty()) {
            ListTag list = new ListTag();
            for (Map.Entry<BlockPos, CompoundTag> entry : this.blockEntities.entrySet()) {
                CompoundTag voxelTag = new CompoundTag();
                voxelTag.putInt("X", entry.getKey().getX());
                voxelTag.putInt("Y", entry.getKey().getY());
                voxelTag.putInt("Z", entry.getKey().getZ());
                voxelTag.put("Data", entry.getValue().copy());
                list.add(voxelTag);
            }
            tag.put("BlockEntities", list);
        }
    }

    /**
     * Right-click interaction with the hull.
     *
     * <p>A block in hand bolts that block onto the hull; a chest or barrel on the hull is
     * opened. The hull is turned back into blocks with a physics switch: place one next to
     * the hull and switch it on (the switch snaps back off once the hull is dissolved).</p>
     *
     * <ul>
     *     <li>right-click with a block: extend the hull (the block becomes part of the body)</li>
     *     <li>right-click with an empty hand on a chest: open it</li>
     *     <li>right-click with an empty hand anywhere else: report blocks / mass / speed</li>
     * </ul>
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.structure == null) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);

        if (stack.getItem() instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            if (this.attachBlock(player, state)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                player.displayClientMessage(Component.translatable(
                        "message.astranautics.ship.attached",
                        this.structure == null ? 0 : this.structure.voxels().size())
                        .withStyle(ChatFormatting.AQUA), true);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("message.astranautics.ship.cannot_attach")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        // A chest / barrel / shulker box on the hull can be opened normally.
        VoxelHit hit = this.raycastHull(player.getEyePosition(), player.getLookAngle());
        if (hit != null && this.openContainer(level, player, hit.cell())) {
            return InteractionResult.CONSUME;
        }

        // Switching the hull off is done with a physics switch (place one next to the
        // hull and right-click it), so an empty hand only reports the state.
        player.displayClientMessage(Component.translatable("message.astranautics.ship.status",
                this.structure.voxels().size(),
                String.format("%.1f", this.body.getMass()),
                String.format("%.2f", this.body.linearVelocity.length()))
                .withStyle(ChatFormatting.GOLD), false);
        return InteractionResult.CONSUME;
    }

    /** A hit of the view ray against the hull: the voxel and the face that was hit. */
    private record VoxelHit(BlockPos cell, int axis, int sign) {
        BlockPos outsideCell() {
            return switch (this.axis) {
                case 0 -> this.cell.offset(this.sign, 0, 0);
                case 1 -> this.cell.offset(0, this.sign, 0);
                default -> this.cell.offset(0, 0, this.sign);
            };
        }
    }

    /** Adds a block to the rigid body at the cell the player is looking at. */
    private boolean attachBlock(Player player, BlockState state) {
        ShipStructure current = this.structure;
        if (current == null) {
            return false;
        }
        if (current.voxels().size() >= AstraConfig.MAX_BLOCKS.get()) {
            return false;
        }

        VoxelHit hit = this.raycastHull(player.getEyePosition(), player.getLookAngle());
        if (hit == null) {
            return false;
        }
        BlockPos target = hit.outsideCell();
        if (current.voxels().containsKey(target)) {
            return false;
        }

        Map<BlockPos, BlockState> voxels = new LinkedHashMap<>(current.voxels());
        voxels.put(target, state);
        this.applyVoxels(ShipStructure.of(voxels));
        return true;
    }

    /** Removes one block of the hull and drops it (with its contents). */
    public boolean breakVoxelAt(ServerLevel level, BlockPos cell) {
        ShipStructure current = this.structure;
        if (current == null) {
            return false;
        }
        BlockState state = current.voxels().get(cell);
        if (state == null) {
            return false;
        }

        Vector3d dropAt = new Vector3d();
        this.voxelWorldCenter(cell, dropAt);
        BlockPos dropPos = BlockPos.containing(dropAt.x, dropAt.y, dropAt.z);

        Block.popResource(level, dropPos, new ItemStack(state.getBlock()));
        CompoundTag stored = this.blockEntities.remove(cell);
        if (stored != null) {
            ListTag items = stored.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = ItemStack.of(items.getCompound(i));
                if (!stack.isEmpty()) {
                    Block.popResource(level, dropPos, stack);
                }
            }
        }

        level.playSound(null, dropAt.x, dropAt.y, dropAt.z,
                state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
        level.levelEvent(2001, dropPos, Block.getId(state));

        Map<BlockPos, BlockState> voxels = new LinkedHashMap<>(current.voxels());
        voxels.remove(cell);
        if (voxels.isEmpty()) {
            // Nothing left to simulate.
            this.structure = null;
            this.discard();
            return true;
        }
        this.applyVoxels(ShipStructure.of(voxels));
        return true;
    }

    /**
     * Rebuilds the rigid body after a voxel was added or removed. The centre of mass
     * moves, so the body is shifted by {@code R * (com_new - com_old)} to keep the rest
     * of the hull exactly where it is in the world.
     */
    private void applyVoxels(ShipStructure rebuilt) {
        ShipStructure current = this.structure;
        if (current == null) {
            this.structure = rebuilt;
            return;
        }

        Vector3d shift = new Vector3d(rebuilt.getCenterOfMass()).sub(current.getCenterOfMass());
        this.body.getRotationMatrix().transform(shift);
        this.body.position.add(shift);

        double oldMass = current.getMass();
        double newMass = rebuilt.getMass();
        if (newMass > 1.0E-4D) {
            // Roughly conserve momentum: a heavy hull hardly slows down.
            this.body.linearVelocity.mul(oldMass / newMass);
        }
        this.body.setMassProperties(newMass, rebuilt.getInertiaLocal());

        this.structure = rebuilt;
        this.structureSyncPending = true;
        this.structureSyncTicks = 20;
        this.spatialIndex = null;
        this.setPos(this.body.position.x, this.body.position.y, this.body.position.z);
        this.updateBoundingExtents();
        this.wake();
    }

    /**
     * Opens a chest / barrel / shulker box that travels with the hull. The contents are
     * kept in the per-voxel block entity data.
     */
    private boolean openContainer(ServerLevel level, Player player, BlockPos cell) {
        ShipStructure current = this.structure;
        if (current == null) {
            return false;
        }
        BlockState state = current.voxels().get(cell);
        if (state == null) {
            return false;
        }
        if (!(state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock)) {
            return false;
        }

        CompoundTag data = this.blockEntities.computeIfAbsent(cell, key -> new CompoundTag());
        ShipContainer container = new ShipContainer(data);
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, opener) -> ChestMenu.threeRows(id, inventory, container),
                Component.translatable(state.getBlock().getDescriptionId())));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.6F, 1.0F);
        return true;
    }

    /** Local voxel of the hull the given ray hits first, or {@code null}. */
    @Nullable
    public BlockPos raycastCell(Vec3 eye, Vec3 look) {
        VoxelHit hit = this.raycastHull(eye, look);
        return hit == null ? null : hit.cell();
    }

    @Nullable
    private VoxelHit raycastHull(Vec3 eye, Vec3 look) {
        // getStructure() also decodes the synced data on the client.
        ShipStructure current = this.getStructure();
        if (current == null) {
            return null;
        }

        Quaterniond inverse = new Quaterniond(this.body.orientation).conjugate();
        Vector3d com = current.getCenterOfMass();
        Vector3d origin = new Vector3d(eye.x - this.body.position.x,
                eye.y - this.body.position.y, eye.z - this.body.position.z);
        inverse.transform(origin);
        // Grid coordinates: the local frame is shifted by the centre of mass.
        origin.add(com);

        Vector3d direction = new Vector3d(look.x, look.y, look.z);
        inverse.transform(direction);
        if (direction.lengthSquared() < 1.0E-9D) {
            return null;
        }
        direction.normalize();

        double bestT = Double.MAX_VALUE;
        BlockPos bestCell = null;
        int bestAxis = -1;
        int bestSign = 0;

        for (BlockPos cell : current.voxels().keySet()) {
            double tMin = 0.0D;
            double tMax = Double.MAX_VALUE;
            int axis = -1;
            int sign = 0;
            boolean miss = false;

            for (int a = 0; a < 3; a++) {
                double o = axisValue(origin, a);
                double d = axisValue(direction, a);
                double min = axisValue(cell, a);
                double max = min + 1.0D;

                if (Math.abs(d) < 1.0E-9D) {
                    if (o < min || o > max) {
                        miss = true;
                        break;
                    }
                    continue;
                }

                double t1 = (min - o) / d;
                double t2 = (max - o) / d;
                if (t1 > t2) {
                    double swap = t1;
                    t1 = t2;
                    t2 = swap;
                }
                if (t1 > tMin) {
                    tMin = t1;
                    axis = a;
                    sign = d > 0.0D ? -1 : 1;
                }
                if (t2 < tMax) {
                    tMax = t2;
                }
                if (tMin > tMax) {
                    miss = true;
                    break;
                }
            }

            if (miss || axis < 0 || tMin < 0.0D || tMin >= bestT) {
                continue;
            }
            bestT = tMin;
            bestCell = cell;
            bestAxis = axis;
            bestSign = sign;
        }

        if (bestCell == null || bestT > 6.0D) {
            return null;
        }
        return new VoxelHit(bestCell, bestAxis, bestSign);
    }

    private static double axisValue(Vector3d vector, int axis) {
        return axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z;
    }

    private static double axisValue(Vec3 vector, int axis) {
        return axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z;
    }

    private static double axisValue(BlockPos pos, int axis) {
        return axis == 0 ? pos.getX() : axis == 1 ? pos.getY() : pos.getZ();
    }

    private static void readItems(CompoundTag data, NonNullList<ItemStack> items) {
        ListTag list = data.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot < items.size()) {
                items.set(slot, ItemStack.of(entry));
            }
        }
    }

    private static void writeItems(CompoundTag data, NonNullList<ItemStack> items) {
        ListTag list = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = stack.save(new CompoundTag());
            entry.putByte("Slot", (byte) i);
            list.add(entry);
        }
        data.put("Items", list);
    }

    /** Inventory of one container block that lives on the hull. */
    private final class ShipContainer implements Container {
        private final CompoundTag data;
        private final NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

        private ShipContainer(CompoundTag data) {
            this.data = data;
            readItems(this.data, this.items);
        }

        @Override
        public int getContainerSize() {
            return this.items.size();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : this.items) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return this.items.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack removed = net.minecraft.world.ContainerHelper.removeItem(this.items, slot, amount);
            if (!removed.isEmpty()) {
                this.setChanged();
            }
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return net.minecraft.world.ContainerHelper.takeItem(this.items, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            this.items.set(slot, stack);
            this.setChanged();
        }

        @Override
        public void setChanged() {
            writeItems(this.data, this.items);
        }

        @Override
        public boolean stillValid(Player player) {
            return !PhysicsShip.this.isRemoved();
        }

        @Override
        public void clearContent() {
            this.items.clear();
            this.setChanged();
        }
    }

    /**
     * Punching the structure pushes it: the impulse is applied at the surface voxel
     * closest to the attacker, so hitting it off-centre also spins it.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) {
            // Let the client play the swing animation; the server does the physics.
            return true;
        }
        if (!(this.level() instanceof ServerLevel level) || this.structure == null) {
            return false;
        }

        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (attacker == null && direct == null) {
            // Environmental damage: the structure is indestructible.
            return false;
        }

        // While the player holds the attack button the hull is being mined: do not
        // punch it away from under the crosshair.
        if (attacker instanceof Player player) {
            BlockPos target = this.raycastCell(player.getEyePosition(), player.getLookAngle());
            if (target != null && VoxelMining.isMining((ServerPlayer) player, this, target)) {
                return true;
            }
        }

        Vector3d point = new Vector3d(this.body.position);
        Vector3d direction = new Vector3d();
        BlockState hitState = null;

        if (attacker != null) {
            Vector3d eye = new Vector3d(attacker.getX(), attacker.getEyeY(), attacker.getZ());
            if (eye.distanceSquared(this.body.position) > 64.0D) {
                return false; // out of punching range, ignore stray "hits"
            }
            BlockPos voxel = nearestSurfaceVoxel(eye);
            if (voxel != null) {
                this.voxelWorldCenter(voxel, point);
                hitState = this.structure.voxels().get(voxel);
            }
            direction.set(point).sub(eye);
            if (direction.lengthSquared() < 1.0E-6D) {
                Vec3 look = attacker.getLookAngle();
                direction.set(look.x, look.y, look.z);
            }
            direction.normalize();
        } else {
            Vec3 motion = direct.getDeltaMovement();
            direction.set(motion.x, motion.y, motion.z);
            if (direction.lengthSquared() < 1.0E-6D) {
                return false;
            }
            direction.normalize();
        }

        double mass = Math.max(this.body.getMass(), 1.0E-4D);
        // Heavy structures barely budge, small ones fly off: a soft mass falloff keeps
        // the punch feeling similar for a 3-block crate and a 200-block hull.
        double scale = 48.0D / (48.0D + mass);
        double impulseMagnitude = AstraConfig.PUNCH_SPEED.get() * scale * mass;

        this.wake();
        Vector3d impulse = new Vector3d(direction).mul(impulseMagnitude);
        this.body.applyImpulseAt(impulse, point);
        this.spatialIndex = null;

        if (attacker instanceof Player player) {
            this.lastPunchPlayer = player.getUUID();
            this.lastPunchTick = level.getGameTime();
            this.lastPunchImpulse.set(impulse);
            this.lastPunchPoint.set(point);
        }

        if (hitState != null) {
            level.playSound(null, point.x, point.y, point.z,
                    hitState.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.9F,
                    0.85F + level.random.nextFloat() * 0.3F);
        } else {
            level.playSound(null, point.x, point.y, point.z,
                    SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.9F, 1.0F);
        }
        return true;
    }

    /**
     * Takes back the punch of {@code player} when it happened a moment ago, so that a
     * click which turns into a held left click does not shove the hull away while the
     * player is trying to mine it.
     */
    public boolean undoLastPunch(ServerLevel level, Player player) {
        if (this.lastPunchPlayer == null || !this.lastPunchPlayer.equals(player.getUUID())) {
            return false;
        }
        this.lastPunchPlayer = null;
        if (level.getGameTime() - this.lastPunchTick > 6L) {
            return false;
        }
        this.body.applyImpulseAt(new Vector3d(this.lastPunchImpulse).negate(), this.lastPunchPoint);
        this.spatialIndex = null;
        return true;
    }

    /** Local voxel (on the surface) closest to a world position. */
    @Nullable
    private BlockPos nearestSurfaceVoxel(Vector3d worldFrom) {
        ShipStructure current = this.structure;
        if (current == null) {
            return null;
        }
        Matrix3d rotation = this.body.getRotationMatrix();
        Vector3d scratch = new Vector3d();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos voxel : current.surfaceVoxels()) {
            current.localCenterRelativeToCom(voxel, scratch);
            rotation.transform(scratch).add(this.body.position);
            double distance = scratch.distanceSquared(worldFrom);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = voxel;
            }
        }
        return best;
    }

    /** World-space centre of a local voxel. */
    private void voxelWorldCenter(BlockPos local, Vector3d dest) {
        if (this.structure == null) {
            dest.set(this.body.position);
            return;
        }
        this.structure.localCenterRelativeToCom(local, dest);
        this.body.getRotationMatrix().transform(dest).add(this.body.position);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}

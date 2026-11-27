package com.astranautics.entity;

import com.astranautics.config.AstraConfig;
import com.astranautics.physics.ContactCache;
import com.astranautics.physics.PhysicsParams;
import com.astranautics.physics.RigidBody;
import com.astranautics.physics.ShipPhysics;
import com.astranautics.physics.ShipStructure;
import com.astranautics.physics.ShipStructureCodec;
import com.astranautics.physics.ShipWorld;
import com.astranautics.physics.VoxelSpatialIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Map;

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
    /** Remaining ticks during which the structure is (re)sent, so late tracking still receives it. */
    private int structureSyncTicks;
    /** Half extents of the world-space bounding box, derived from the structure and rotation. */
    private final Vector3d halfExtents = new Vector3d(0.5D, 0.5D, 0.5D);
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

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float width = (float) Math.max(0.5D, Math.max(halfExtents.x, halfExtents.z) * 2.0D);
        float height = (float) Math.max(0.5D, halfExtents.y * 2.0D);
        return EntityDimensions.scalable(width, height);
    }

    @Override
    protected AABB makeBoundingBox() {
        return new AABB(getX() - halfExtents.x, getY() - halfExtents.y, getZ() - halfExtents.z,
                getX() + halfExtents.x, getY() + halfExtents.y, getZ() + halfExtents.z);
    }

    /**
     * Recomputes the axis-aligned world extents of the rotated structure so that
     * culling, entity collisions and the ship-vs-ship broad phase all see the real
     * size of the ship instead of a 1x1x1 box.
     */
    private void updateBoundingExtents() {
        ShipStructure current = this.structure;
        if (current == null) {
            halfExtents.set(0.5D, 0.5D, 0.5D);
            return;
        }

        AABB local = current.getLocalBounds();
        Vector3d com = current.getCenterOfMass();
        double hx = Math.max(Math.abs(local.minX - com.x), Math.abs(local.maxX - com.x));
        double hy = Math.max(Math.abs(local.minY - com.y), Math.abs(local.maxY - com.y));
        double hz = Math.max(Math.abs(local.minZ - com.z), Math.abs(local.maxZ - com.z));

        Matrix3d rotation = this.body.getRotationMatrix();
        halfExtents.set(
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
            this.updateBoundingExtents();
            return;
        }

        this.tickServer((ServerLevel) this.level());
    }

    private void tickServer(ServerLevel level) {
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
                if (level.isEmptyBlock(target) || level.getBlockState(target).canBeReplaced()) {
                    level.setBlock(target, state, 3);
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
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}

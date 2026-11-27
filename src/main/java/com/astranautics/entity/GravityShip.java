package com.astranautics.entity;

import com.astranautics.config.AstraConfig;
import com.astranautics.physics.RigidBody;
import com.astranautics.physics.ShipPhysics;
import com.astranautics.physics.ShipStructure;
import com.astranautics.physics.ShipStructureCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Map;

/**
 * A block structure that has been detached from the world and is simulated as a
 * single rigid body.
 *
 * <p>The entity position is the centre of mass; the voxel grid is kept in local
 * coordinates and rendered with the synced orientation quaternion.</p>
 */
public class GravityShip extends Entity {
    private static final EntityDataAccessor<String> DATA_STRUCTURE =
            SynchedEntityData.defineId(GravityShip.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_QX =
            SynchedEntityData.defineId(GravityShip.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QY =
            SynchedEntityData.defineId(GravityShip.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QZ =
            SynchedEntityData.defineId(GravityShip.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QW =
            SynchedEntityData.defineId(GravityShip.class, EntityDataSerializers.FLOAT);

    private final RigidBody body = new RigidBody();
    private ShipStructure structure;
    private boolean structureSyncPending;

    // Client-side interpolation of the orientation
    private final Quaterniond currentClientOrientation = new Quaterniond();
    private final Quaterniond previousClientOrientation = new Quaterniond();
    private boolean clientOrientationKnown;

    public GravityShip(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
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
        this.pushOrientation();
    }

    public ShipStructure getStructure() {
        if (this.structure == null && this.level().isClientSide) {
            this.structure = ShipStructureCodec.decode(this.entityData.get(DATA_STRUCTURE));
        }
        return this.structure;
    }

    public RigidBody getBody() {
        return body;
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
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            this.previousClientOrientation.set(this.currentClientOrientation);
            this.currentClientOrientation.set(readSyncedOrientation());
            this.clientOrientationKnown = true;
            return;
        }

        this.tickServer((ServerLevel) this.level());
    }

    private void tickServer(ServerLevel level) {
        if (this.structure == null) {
            return;
        }

        if (this.structureSyncPending) {
            this.entityData.set(DATA_STRUCTURE, ShipStructureCodec.encode(this.structure));
            this.structureSyncPending = false;
        }

        int substeps = Math.max(1, AstraConfig.SUBSTEPS.get());
        double h = (1.0D / 20.0D) / substeps;
        for (int i = 0; i < substeps; i++) {
            ShipPhysics.step(level, this.body, this.structure, h);
        }

        this.setPos(body.position.x, body.position.y, body.position.z);
        this.pushOrientation();

        if (body.position.y < level.getMinBuildHeight() - 48.0D) {
            // Fell out of the world: give the blocks back as items at the void edge.
            dissolve(level);
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

    /** Unused, but keeps the capability for future EMP-style torque effects. */
    public void addTorque(Vector3d worldTorque) {
        this.body.applyAngularImpulse(worldTorque);
    }
}

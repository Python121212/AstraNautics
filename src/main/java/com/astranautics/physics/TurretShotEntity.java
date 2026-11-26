package com.astranautics.physics;

import com.astranautics.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 艦載砲の弾。真っ直ぐ飛び、当たると爆発します。
 *
 * <p>船（{@link ShipEntity}）には当たりません（同士討ち防止）。
 * 8 秒で消滅、ブロックに当たっても消えます。</p>
 */
public class TurretShotEntity extends Entity {

    /** 弾の寿命（tick）。 */
    private static final int LIFETIME = 160;
    /** ダメージ。 */
    private static final float DAMAGE = 12.0F;
    /** 爆発半径。 */
    private static final float EXPLOSION = 1.6F;

    private Entity owner;
    private int life;

    public TurretShotEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public TurretShotEntity(Level level, double x, double y, double z,
                            double mx, double my, double mz) {
        this(ModEntities.TURRET_SHOT.get(), level);
        this.setPos(x, y, z);
        this.setDeltaMovement(mx, my, mz);
    }

    public void setOwner(Entity owner) {
        this.owner = owner;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;
        if (this.life > LIFETIME) {
            this.discard();
            return;
        }

        Vec3 velocity = this.getDeltaMovement();
        Vec3 next = this.position().add(velocity);
        this.setPos(next.x, next.y, next.z);

        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.END_ROD, next.x, next.y, next.z,
                    0.0D, 0.0D, 0.0D);
            return;
        }

        // 船には当たらない（同士討ち防止）／生き物には当たる
        AABB hitBox = this.getBoundingBox().inflate(0.4D);
        for (LivingEntity living : this.level().getEntitiesOfClass(LivingEntity.class, hitBox)) {
            if (living == this.owner) {
                continue;
            }
            living.hurt(this.damageSources().magic(), DAMAGE);
            this.explode();
            return;
        }

        // ブロックに当たったら爆発
        if (!this.level().getBlockState(this.blockPosition()).isAir()) {
            this.explode();
        }
    }

    private void explode() {
        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.explode(this.owner, this.getX(), this.getY(), this.getZ(), EXPLOSION,
                    Level.ExplosionInteraction.NONE);
        }
        this.discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Life", this.life);
    }
}

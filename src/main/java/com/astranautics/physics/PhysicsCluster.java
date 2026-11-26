package com.astranautics.physics;

import com.astranautics.block.PhysicalizedBlockEntity;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 連結した物理化ブロックのまとまり（将来の「宇宙船 1 隻 = 1 剛体」の土台）。
 *
 * <p>現時点では「集合の合計質量・摩擦・反発」を持ち、接触時の力の配分に使います。
 * 将来、このクラスタ単位で剛体（位置・速度・姿勢）を持たせれば、
 * 物理化ブロックを組み上げた宇宙船をそのまま飛ばせる設計です。</p>
 */
public final class PhysicsCluster {

    private final List<PhysicalizedBlockEntity> cells = new ArrayList<>(8);
    private double totalMass = 1.0D;
    private double avgFriction = 0.75D;
    private double avgRestitution = 0.3D;

    public void add(PhysicalizedBlockEntity be) {
        if (!this.cells.contains(be)) {
            this.cells.add(be);
            recompute();
        }
    }

    public void remove(PhysicalizedBlockEntity be) {
        if (this.cells.remove(be)) {
            recompute();
        }
    }

    public boolean contains(PhysicalizedBlockEntity be) {
        return this.cells.contains(be);
    }

    public int size() {
        return this.cells.size();
    }

    public boolean isEmpty() {
        return this.cells.isEmpty();
    }

    public List<PhysicalizedBlockEntity> cells() {
        return this.cells;
    }

    public double totalMass() {
        return this.totalMass;
    }

    public double avgFriction() {
        return this.avgFriction;
    }

    public double avgRestitution() {
        return this.avgRestitution;
    }

    /** 質量で重み付けした速度（構造体としての代表速度）。 */
    public double[] massWeightedVelocity() {
        double vx = 0.0D;
        double vy = 0.0D;
        double vz = 0.0D;
        double m = 0.0D;
        for (PhysicalizedBlockEntity be : this.cells) {
            double cellMass = be.getMaterial().mass();
            vx += be.getVelX() * cellMass;
            vy += be.getVelY() * cellMass;
            vz += be.getVelZ() * cellMass;
            m += cellMass;
        }
        if (m <= 0.0D) {
            return new double[]{0.0D, 0.0D, 0.0D};
        }
        return new double[]{vx / m, vy / m, vz / m};
    }

    /** 全セルに、質量で重み付けしたインパルスを与える（軽いセルほど大きく動く）。 */
    public void applyImpulse(double ix, double iy, double iz) {
        for (PhysicalizedBlockEntity be : this.cells) {
            double share = 1.0D / Math.max(0.2D, be.getMaterial().mass());
            be.addImpulse(ix * share, iy * share, iz * share);
        }
    }

    /** 全セルを起こす（スリープ解除）。 */
    public void wakeAll() {
        for (PhysicalizedBlockEntity be : this.cells) {
            be.wake();
        }
    }

    /** 全セルがスリープしているか。 */
    public boolean isSleeping() {
        for (PhysicalizedBlockEntity be : this.cells) {
            if (be.getSleepTicks() < LightPhysicsEngine.SLEEP_TICKS) {
                return false;
            }
        }
        return true;
    }

    private void recompute() {
        double mass = 0.0D;
        double friction = 0.0D;
        double restitution = 0.0D;
        for (PhysicalizedBlockEntity be : this.cells) {
            MaterialProfile profile = be.getMaterial();
            mass += profile.mass();
            friction += profile.friction();
            restitution += profile.restitution();
        }
        int n = Math.max(1, this.cells.size());
        this.totalMass = Math.max(0.1D, mass);
        this.avgFriction = friction / n;
        this.avgRestitution = restitution / n;
    }

    /** クラスタの簡易 ID（デバッグ表示用）。 */
    public String debugId() {
        if (this.cells.isEmpty()) {
            return "empty";
        }
        BlockPos p = this.cells.get(0).getBlockPos();
        return "cluster@" + p.getX() + "," + p.getY() + "," + p.getZ() + " (" + this.cells.size() + " cells, m="
                + String.format("%.1f", this.totalMass) + ")";
    }

    @Override
    public String toString() {
        return debugId();
    }
}

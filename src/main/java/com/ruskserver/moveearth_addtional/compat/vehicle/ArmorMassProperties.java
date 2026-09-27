package com.ruskserver.moveearth_addtional.compat.vehicle;

import org.joml.Matrix3d;
import org.joml.Vector3d;

/** Unit-cube armor mass, using the parallel-axis theorem for both aggregation and merging. */
public final class ArmorMassProperties {
    private double mass;
    private final Vector3d center = new Vector3d();
    private final Matrix3d inertia = new Matrix3d().zero();

    public double mass() { return mass; }
    public Vector3d center() { return new Vector3d(center); }
    public Matrix3d inertia() { return new Matrix3d(inertia); }

    public void add(double weight, double x, double y, double z) {
        if (!(weight > 0.0) || !Double.isFinite(weight)) return;
        Vector3d delta = new Vector3d(x, y, z).sub(center);
        double total = mass + weight;
        shift(inertia, mass * weight / total, delta);
        double cube = weight / 6.0;
        inertia.m00(inertia.m00() + cube).m11(inertia.m11() + cube).m22(inertia.m22() + cube);
        center.fma(weight / total, delta);
        mass = total;
    }

    /** Mutates base COM/tensor, returning the combined mass. Base inputs must be fresh each call. */
    public double merge(double baseMass, Vector3d baseCenter, Matrix3d baseInertia,
                        double originX, double originY, double originZ) {
        if (mass == 0.0) return baseMass;
        Vector3d delta = new Vector3d(center).add(originX, originY, originZ).sub(baseCenter);
        double total = baseMass + mass;
        baseInertia.add(inertia);
        shift(baseInertia, baseMass * mass / total, delta);
        baseCenter.fma(mass / total, delta);
        return total;
    }

    private static void shift(Matrix3d tensor, double weight, Vector3d d) {
        tensor.m00(tensor.m00() + weight * (d.y * d.y + d.z * d.z));
        tensor.m11(tensor.m11() + weight * (d.x * d.x + d.z * d.z));
        tensor.m22(tensor.m22() + weight * (d.x * d.x + d.y * d.y));
        tensor.m01(tensor.m01() - weight * d.x * d.y);
        tensor.m10(tensor.m10() - weight * d.x * d.y);
        tensor.m02(tensor.m02() - weight * d.x * d.z);
        tensor.m20(tensor.m20() - weight * d.x * d.z);
        tensor.m12(tensor.m12() - weight * d.y * d.z);
        tensor.m21(tensor.m21() - weight * d.y * d.z);
    }
}

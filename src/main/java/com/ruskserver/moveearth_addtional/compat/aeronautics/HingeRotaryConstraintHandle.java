package com.ruskserver.moveearth_addtional.compat.aeronautics;

import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import org.joml.Vector3d;

/**
 * Keeps Create: Simulated's expected rotary-handle API while delegating to a
 * generic constraint whose sole free rotation axis is local Y.
 */
public final class HingeRotaryConstraintHandle implements RotaryConstraintHandle {
    private final GenericConstraintHandle delegate;

    public HingeRotaryConstraintHandle(GenericConstraintHandle delegate) {
        this.delegate = delegate;
    }

    @Override
    public void getJointImpulses(Vector3d linearImpulse, Vector3d angularImpulse) {
        delegate.getJointImpulses(linearImpulse, angularImpulse);
    }

    @Override
    public void setContactsEnabled(boolean enabled) {
        delegate.setContactsEnabled(enabled);
    }

    @Override
    public void setMotor(ConstraintJointAxis axis, double goal, double positionStrength,
                         double velocityStrength, boolean velocityMotor, double maxForce) {
        ConstraintJointAxis mappedAxis = axis == RotaryConstraintHandle.DEFAULT_AXIS
                ? ConstraintJointAxis.ANGULAR_Y
                : axis;
        delegate.setMotor(mappedAxis, goal, positionStrength, velocityStrength, velocityMotor, maxForce);
    }

    @Override
    public void remove() {
        delegate.remove();
    }

    @Override
    public boolean isValid() {
        return delegate.isValid();
    }
}

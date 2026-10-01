package com.ruskserver.moveearth_addtional.compat.kinematics;

/** Added to Absolute Kinematics joints so MoveEarth can read their state without a compile dependency. */
public interface KinematicsJointView {
    /** Whether this is a server-side, assembled, locked joint whose servo is driving an attached leaf. */
    boolean moveearth$drivesAttachedBody();
}

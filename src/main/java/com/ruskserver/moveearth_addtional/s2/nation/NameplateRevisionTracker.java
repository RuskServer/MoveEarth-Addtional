package com.ruskserver.moveearth_addtional.s2.nation;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A revision that moves only when a derived state really changes. The state is rebuilt at most once per
 * source revision and compared by value, so source changes that leave it equal (role edits,
 * applications, ...) keep the revision where it was.
 */
final class NameplateRevisionTracker<S> {
    private long revision;
    private long checkedSourceRevision;
    private boolean checked;
    private S state;

    long revision(long sourceRevision, Supplier<S> currentState) {
        if (!checked || checkedSourceRevision != sourceRevision) {
            checked = true;
            checkedSourceRevision = sourceRevision;
            S current = currentState.get();
            if (!Objects.equals(current, state)) {
                state = current;
                revision++;
            }
        }
        return revision;
    }
}

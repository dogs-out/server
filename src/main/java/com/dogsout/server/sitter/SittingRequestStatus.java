package com.dogsout.server.sitter;

/**
 * ⚠️ Adding a value needs the stale CHECK constraint dropped in production first
 * — see the note on User.walkStatus. The column is declared varchar(16) so a
 * freshly created database does not generate one at all.
 */
public enum SittingRequestStatus {
    /** Anyone may still pick it up. */
    OPEN,
    /** The owner found someone, or no longer needs one. */
    CLOSED
}

package com.blighteddreams.helper.agent;

import com.blighteddreams.helper.HelperEntity;

/**
 * One thing Emily is doing. Tasks are ticked on the server thread and own her movement while they run.
 * Combat goals always win: the agent pauses the task whenever she has a target.
 */
public interface EmilyTask {

    enum Status {
        RUNNING,
        DONE,
        FAILED
    }

    /** Stable name for logs, the status command and the synced task label. */
    String name();

    /** Short human-readable line, e.g. "mining iron (4/16)". */
    default String label() {
        return name();
    }

    /** Called once when the task becomes active. */
    default void start(HelperEntity helper, EmilyAgent agent) {
    }

    /** Called every server tick while she has no combat target and is not downed. */
    Status tick(HelperEntity helper, EmilyAgent agent);

    /** Called once when the task ends for any reason. Must not throw. */
    default void stop(HelperEntity helper, EmilyAgent agent) {
    }

    /**
     * True while the task wants to be far from the owner. {@code HelperManager} then leaves her alone instead of
     * teleporting her back every time she walks out of range.
     */
    default boolean roaming() {
        return true;
    }

    /** True if this task should survive the owner leaving the dimension. */
    default boolean crossDimension() {
        return false;
    }

    /** True when the task already says its own completion line, so the agent does not repeat it. */
    default boolean quietOnDone() {
        return false;
    }
}

package dev.ghostfusion.ui;

/**
 * Tiny owner-thread fault boundary for the warning painter. This is ONLY a
 * presentation guard: a failed draw never mutates native pulse quarantine,
 * module state, recovery authority, or server output.
 *
 * RuntimeException is contained so other HUD elements still draw; JVM Errors
 * intentionally propagate. A later frame can try drawing the same warning.
 */
public final class HudWarningDrawGate {
    private final Thread owner=Thread.currentThread();
    private boolean failed;
    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("warning painter owner thread");
    }
    public boolean failed() {
        check();
        return failed;
    }
    public boolean draw(Runnable painter) {
        check();
        if(painter==null)throw new IllegalArgumentException("warning painter");
        try {
            painter.run();
            failed=false;
            return true;
        } catch(RuntimeException renderFailure) {
            failed=true;
            return false;
        }
    }
    /** Absence of a warning is not a successful recovered draw. */
    public void idle() {
        check();
        failed=false;
    }
}

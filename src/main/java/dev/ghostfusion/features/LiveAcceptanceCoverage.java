package dev.ghostfusion.features;

/**
 * Owner-thread in-game acceptance coverage; observes actual native tick
 * decisions and aggregate Netty hook evidence supplied by the Forge bridge.
 * No game objects, packet data, player/server identity or coordinates stored.
 *
 * Evidence is monotonic within this client process. A complete set means
 * exercised locally, NOT multiplayer/server/gameplay certification.
 */
public final class LiveAcceptanceCoverage {
    public enum Check {
        GAMEPLAY, CONCURRENT, ARMOR, SORTER, CHEST, GUI_BLOCK,
        FOCUS_BLOCK, WTAP_HOLD, HOOK_ATTACHED, S12_LOCAL, S27_EXPLOSION
    }
    private final Thread owner=Thread.currentThread();
    private int seen;
    private String hookStatus="DISABLED";
    private static final int ALL=(1<<Check.values().length)-1;

    private void checkOwner() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("acceptance evidence owner thread");
    }
    /**
     * Returns NEW evidence bits, suitable for recording exactly once with
     * RuntimeDiagnostics. Counters passed from Netty are already anonymous
     * monotonic totals; no packet listener is registered by this class.
     */
    public int observe(ModuleInteractionStateMachine.Frame frame,
                       String status,long localS12,long explosionS27) {
        checkOwner();
        if(frame==null || status==null || localS12<0 || explosionS27<0)
            throw new IllegalArgumentException("invalid acceptance sample");
        hookStatus=normalizeStatus(status);
        int marks=0;
        if(frame.phase==ModuleInteractionStateMachine.Phase.GAMEPLAY)
            marks|=bit(Check.GAMEPLAY);
        if(frame.combat && frame.bridge && frame.velocity)
            marks|=bit(Check.CONCURRENT);
        if(frame.autoArmor)marks|=bit(Check.ARMOR);
        if(frame.inventoryManager)marks|=bit(Check.SORTER);
        if(frame.chestStealer)marks|=bit(Check.CHEST);
        if(frame.phase==ModuleInteractionStateMachine.Phase.UI_PAUSED ||
           (frame.phase==ModuleInteractionStateMachine.Phase.PLAYER_ARMOR &&
            frame.denial(ModuleInteractionStateMachine.Resource.COMBAT)==
                ModuleInteractionStateMachine.Denial.WRONG_SCREEN))
            marks|=bit(Check.GUI_BLOCK);
        if(frame.denial(ModuleInteractionStateMachine.Resource.VELOCITY)==
                ModuleInteractionStateMachine.Denial.WINDOW_INACTIVE ||
           frame.denial(ModuleInteractionStateMachine.Resource.COMBAT)==
                ModuleInteractionStateMachine.Denial.WINDOW_INACTIVE)
            marks|=bit(Check.FOCUS_BLOCK);
        if(frame.sprintOwner==ModuleInteractionStateMachine.SprintOwner.WTAP &&
           !frame.autoSprint)marks|=bit(Check.WTAP_HOLD);
        if(frame.velocity && "ATTACHED".equals(hookStatus))
            marks|=bit(Check.HOOK_ATTACHED);
        // Packet observations can be sampled in a later frame (including a
        // GUI/pause frame) because Netty and the Minecraft client thread
        // are intentionally asynchronous. No false 'packet hit' claim.
        if(localS12>0)marks|=bit(Check.S12_LOCAL);
        if(explosionS27>0)marks|=bit(Check.S27_EXPLOSION);
        int newly=marks&~seen;
        seen|=marks;
        return newly;
    }
    private static String normalizeStatus(String value) {
        if("ATTACHED".equals(value)||"ATTACH_PENDING".equals(value)||
           "MAPPING_MISSING".equals(value)||"DISABLED".equals(value))
            return value;
        return "UNAVAILABLE";
    }
    public static int bit(Check check) {
        if(check==null)throw new IllegalArgumentException("check");
        return 1<<check.ordinal();
    }
    public boolean seen(Check check) {
        checkOwner();
        return (seen&bit(check))!=0;
    }
    public int count() {
        checkOwner();
        return Integer.bitCount(seen&ALL);
    }
    public String readout() {
        checkOwner();
        return "Acceptance evidence "+count()+"/"+Check.values().length+
            " | packet hook "+hookStatus;
    }
}

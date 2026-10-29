package dev.ghostfusion.features;

/**
 * Conservative classification of the REAL vanilla Minecraft 1.8.9
 * PlayerControllerMP.sendUseItem(EntityPlayer,World,ItemStack) result.
 *
 * In non-spectator mode vanilla syncs the held item and queues the use-item
 * packet BEFORE ItemStack.useItemRightClick. The boolean method result
 * indicates a local stack reference/count change, NOT packet acceptance.
 * A fishing rod returns the same stack and often produces false even though
 * the native item-use path ran. No server ACK/hit is inferred here.
 *
 * Only the Forge owner calls the original player controller; this helper
 * never creates packets, sends input or touches Minecraft classes.
 */
public final class ProjectileNativeUseResult {
    public enum Outcome {
        NOT_INVOKED(false,false),
        SPECTATOR_DENIED(false,false),
        SUBMITTED_UNCHANGED(true,false),
        SUBMITTED_LOCAL_CHANGE(true,true);
        private final boolean submitted,localChange;
        Outcome(boolean submitted,boolean localChange) {
            this.submitted=submitted;
            this.localChange=localChange;
        }
        public boolean submitted() {return submitted;}
        public boolean localChange() {return localChange;}
    }
    private ProjectileNativeUseResult() {}
    /**
     * The caller MUST establish the exact original player/world/stack/target
     * and call the actual native method before supplying invoked=true.
     */
    public static Outcome classify(boolean spectator,boolean invoked,
                                   boolean nativeReturnLocalChange) {
        if(spectator) {
            if(invoked||nativeReturnLocalChange)
                throw new IllegalArgumentException("spectator native use must not run");
            return Outcome.SPECTATOR_DENIED;
        }
        if(!invoked) {
            if(nativeReturnLocalChange)
                throw new IllegalArgumentException("local change without native invocation");
            return Outcome.NOT_INVOKED;
        }
        return nativeReturnLocalChange ?
            Outcome.SUBMITTED_LOCAL_CHANGE : Outcome.SUBMITTED_UNCHANGED;
    }
}

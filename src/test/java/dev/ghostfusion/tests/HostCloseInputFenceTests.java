package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/** R3.28: a shutdown callback cannot dispatch new features, input or producer events. */
public final class HostCloseInputFenceTests {
    private static int assertions;
    private static void ok(boolean value,String why) {
        assertions++;
        if(!value)throw new AssertionError(why);
    }
    private static final class Probe implements GhostModule {
        final String stableId;
        Runnable disable;
        int disabled;
        Probe(String id){stableId=id;}
        public String id(){return stableId;}
        public String displayName(){return "Close input probe";}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){}
        public void onDisable(){disabled++;if(disable!=null)disable.run();}
        public void onEvent(GameEvent e){}
    }
    private static void closeCallbackInputIsInert() throws Exception {
        for(int i=0;i<192;i++) {
            final int iteration=i;
            Path folder=Files.createTempDirectory("ghostc-r328-close-input");
            HostRuntime host=new HostRuntime(folder);
            Probe attacker=new Probe("test.close_attacker");
            Probe peer=new Probe("test.close_peer");
            host.modules().register(attacker,0);
            host.modules().register(peer,91);
            host.modules().setEnabled(attacker.id(),true);
            host.modules().setEnabled(peer.id(),true);
            host.worldPresence(true);

            // Normal input and feature callbacks must continue working
            // before close begins; only the shutdown window is fenced.
            host.key(91,true,false);
            ok(host.modules().get(peer.id()).state()==ModuleRegistry.State.DISABLED,
               "live hotkey still disables peer "+i);
            host.key(91,false,false);
            host.modules().setEnabled(peer.id(),true);
            final int[] executed={0},seen={0};
            ok(host.runFeature(peer.id(),()->executed[0]++) && executed[0]==1,
               "live guarded feature runs before close "+i);
            host.inputFocus(true);host.inputFocus(false);
            final long focusBefore=host.diagnostics().count(
                RuntimeDiagnostics.Counter.FOCUS_LOST);
            final long featureFaultBefore=host.diagnostics().count(
                RuntimeDiagnostics.Counter.FEATURE_FAULT);

            // Simulate an exhausted producer sequence: during shutdown even
            // asking to enqueue must return false, not throw on ID overflow.
            java.lang.reflect.Field field=HostRuntime.class.getDeclaredField("sequence");
            field.setAccessible(true);
            AtomicLong sequence=(AtomicLong)field.get(host);
            sequence.set(Long.MAX_VALUE);
            final boolean[] guarded={false};
            attacker.disable=()->{
                seen[0]++;
                ok(host.modules().get(peer.id()).state()==ModuleRegistry.State.ENABLED,
                   "peer is still enabled during earlier disable callback "+iteration);
                host.inputFocus(true);
                host.key(91,true,false);
                host.key(91,false,false);
                boolean admitted=host.runFeature(peer.id(),()->executed[0]++);
                boolean published=host.enqueue(GameEvent.Kind.CLIENT_TICK);
                guarded[0]=!admitted && !published &&
                   executed[0]==1 && sequence.get()==Long.MAX_VALUE &&
                   host.diagnostics().count(RuntimeDiagnostics.Counter.FOCUS_LOST)==focusBefore &&
                   host.diagnostics().count(RuntimeDiagnostics.Counter.FEATURE_FAULT)==featureFaultBefore &&
                   host.modules().get(peer.id()).state()==ModuleRegistry.State.ENABLED;
            };
            host.close();
            ok(guarded[0] && seen[0]==1,
               "closing callbacks cannot toggle peers or run features "+i);
            ok(host.isClosed() && sequence.get()==Long.MAX_VALUE &&
               attacker.disabled==1 && peer.disabled==2,
               "terminal cleanup is exactly once and sequence remains intact "+i);
            ok(host.modules().get(attacker.id()).state()==ModuleRegistry.State.DISABLED &&
               host.modules().get(peer.id()).state()==ModuleRegistry.State.DISABLED &&
               host.faultWarning().isEmpty(),
               "no module was spuriously faulted during normal shutdown "+i);
            ok(host.diagnostics().count(RuntimeDiagnostics.Counter.FOCUS_LOST)==focusBefore &&
               host.diagnostics().count(RuntimeDiagnostics.Counter.FEATURE_FAULT)==featureFaultBefore &&
               !host.enqueue(GameEvent.Kind.RENDER_2D),
               "post-close remains inert and diagnostics unmodified "+i);
            // No closed-host function may resurrect feature callbacks.
            ok(!host.runFeature(peer.id(),()->executed[0]++) && executed[0]==1,
               "post-close feature callback cannot run "+i);
        }
    }
    public static void main(String[] args) throws Exception {
        closeCallbackInputIsInert();
        ok(assertions>1200,"broad shutdown-input regression");
        System.out.println("HOST_CLOSE_INPUT_FENCE_PASS assertions="+assertions);
    }
}

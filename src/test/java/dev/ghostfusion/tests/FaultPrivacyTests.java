package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import java.util.Random;

/** R1.6: no private exception messages survive the module lifecycle. */
public final class FaultPrivacyTests {
    private static int checks;
    private static void ok(boolean condition,String context) {
        checks++;
        if (!condition) throw new AssertionError(context);
    }
    private static final class Sensitive implements GhostModule {
        final String id;
        boolean breakEnable,breakEvent,breakDisable;
        String privateString="PLAYER_NAME OR SERVER CHAT SHOULD NEVER LEAK";
        Sensitive(String id) { this.id=id; }
        public String id() { return id; }
        public String displayName() { return "Sensitive fixture"; }
        public Category category() { return Category.UTILITY; }
        public void onEnable() {
            if(breakEnable) throw new IllegalArgumentException(privateString);
        }
        public void onDisable() {
            if(breakDisable) throw new IllegalStateException(privateString);
        }
        public void onEvent(GameEvent e) {
            if(breakEvent) throw new RuntimeException(privateString);
        }
    }
    private static void lifecycle() {
        ModuleRegistry registry=new ModuleRegistry();
        Sensitive a=new Sensitive("privacy.enable");
        Sensitive b=new Sensitive("privacy.event");
        Sensitive c=new Sensitive("privacy.disable");
        registry.register(a,0);registry.register(b,0);registry.register(c,0);
        a.breakEnable=true;
        registry.setEnabled(a.id(),true);
        ok(registry.get(a.id()).state()==ModuleRegistry.State.FAULTED,"enable fault");
        ok("IllegalArgumentException".equals(registry.get(a.id()).lastFault()),"type only enable");
        b.breakEvent=true;
        registry.setEnabled(b.id(),true);
        registry.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK,1));
        ok("RuntimeException".equals(registry.get(b.id()).lastFault()),"type only event");
        c.breakDisable=true;
        registry.setEnabled(c.id(),true);
        registry.setEnabled(c.id(),false);
        ok("IllegalStateException".equals(registry.get(c.id()).lastFault()),"type only disable");
        for(Sensitive s:new Sensitive[]{a,b,c}) {
            ok(!registry.get(s.id()).lastFault().contains(s.privateString),
                "sensitive message dropped");
            registry.setEnabled(s.id(),false);
            ok(registry.get(s.id()).lastFault().isEmpty(),"reset clears fault detail");
        }
        registry.shutdown();
    }
    private static void replay() {
        Random random=new Random(0x20161019L);
        ModuleRegistry registry=new ModuleRegistry();
        Sensitive[] all=new Sensitive[10];
        for(int i=0;i<all.length;i++) {
            all[i]=new Sensitive("privacy.test"+i);
            registry.register(all[i],0);
        }
        for(int i=0;i<4500;i++) {
            int selected=random.nextInt(all.length);
            Sensitive test=all[selected];
            registry.setEnabled(test.id(),false);
            test.breakEnable=false;test.breakEvent=false;test.breakDisable=false;
            registry.setEnabled(test.id(),true);
            test.privateString="USERNAME="+i+" SECRET SESSION DATA";
            test.breakEvent=true;
            registry.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK,i+10));
            ok(registry.get(test.id()).state()==ModuleRegistry.State.FAULTED,
                "isolated fault "+i);
            String type=registry.get(test.id()).lastFault();
            ok("RuntimeException".equals(type),"only exception type saved "+i);
            ok(!type.contains("USERNAME") && !type.contains("SECRET"),
                "message not retained "+i);
        }
        registry.shutdown();
    }
    public static void main(String[] args) {
        lifecycle();replay();
        ok(checks>13000,"broad fault message scrub replay");
        System.out.println("FAULT_PRIVACY_PASS assertions="+checks);
    }
}

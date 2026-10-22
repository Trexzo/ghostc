package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspTeamPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.core.Setting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** R2.66 optional in-team suppression with exact scoreboard identity. */
public final class EspTeamPolicyTests {
    private static int assertions;
    private static void ok(boolean value,String why) {
        assertions++;if(!value)throw new AssertionError(why);
    }
    private static void examples(){
        Object blue=new Object(),red=new Object();
        ok(EspTeamPolicy.exclude(blue,blue),
            "two players on the same scoreboard team are filtered");
        ok(!EspTeamPolicy.exclude(blue,red),
            "different scoreboard teams remain eligible");
        ok(!EspTeamPolicy.exclude(null,null),
            "two players without teams are NOT teammates");
        ok(!EspTeamPolicy.exclude(blue,null),
            "unassigned target remains eligible");
        ok(!EspTeamPolicy.exclude(null,blue),
            "local player without team never suppresses players");
        String label=new String("blue");
        String matchingText=new String("blue");
        ok(label.equals(matchingText) && label!=matchingText,
            "test fixture has equal-looking distinct identities");
        ok(!EspTeamPolicy.exclude(label,matchingText),
            "matching team labels alone never cause exclusion");
        ok(EspTeamPolicy.exclude(label,label),
            "shared scoreboard identity excludes independent of label content");
    }
    private static void teamChanges(){
        Object blue=new Object(),red=new Object();
        Object local=blue,remote=red;
        ok(!EspTeamPolicy.exclude(local,remote),
            "different teams eligible at candidate admission");
        remote=blue;
        ok(EspTeamPolicy.exclude(local,remote),
            "target joined local team before paint: reject now");
        remote=red;
        ok(!EspTeamPolicy.exclude(local,remote),
            "target left team before paint: eligible now");
        local=red;
        ok(EspTeamPolicy.exclude(local,remote),
            "local player switched teams before paint: reject now");
        local=null;
        ok(!EspTeamPolicy.exclude(local,remote),
            "local scoreboard teardown cannot spuriously hide everyone");
    }
    private static void seeded(){
        Random rng=new Random(2662026L);
        Object[] teams={null,new Object(),new Object(),new Object(),
            new String("one"),new String("one")};
        for(int i=0;i<200000;i++){
            Object a=teams[rng.nextInt(teams.length)];
            Object b=teams[rng.nextInt(teams.length)];
            boolean expected=a!=null && a==b;
            ok(EspTeamPolicy.exclude(a,b)==expected,
                "independent same-reference membership "+i);
        }
    }
    private static void persistence()throws Exception{
        Path dir=Files.createTempDirectory("ghostc-r266-team-policy");
        HostRuntime host=new HostRuntime(dir);
        ok(!host.espIgnoreTeammates(),
            "legacy default draws teammate overlays unchanged");
        Setting.Bool setting=(Setting.Bool)host.modules().get("render.esp")
            .settings().get("ignore_teammates");
        ok(!setting.get(),"new bool available through existing typed ClickGUI");
        host.uiActions().bool("render.esp","ignore_teammates");
        ok(host.espIgnoreTeammates(),"user can enable teammate filtering");
        host.persist();host.close();
        HostRuntime loaded=new HostRuntime(dir);
        ok(loaded.espIgnoreTeammates(),
            "opt-in team filter survives profile save/reload");
        ok(loaded.uiActions().reset("render.esp","ignore_teammates"),
            "R2.65 individual-setting reset restores legacy default");
        ok(!loaded.espIgnoreTeammates(),
            "default is disabled after single-setting reset");
        loaded.persist();loaded.close();
        HostRuntime again=new HostRuntime(dir);
        ok(!again.espIgnoreTeammates(),
            "disabled default persists after restart");
        again.close();
    }
    public static void main(String[] args)throws Exception{
        examples();teamChanges();seeded();persistence();
        ok(assertions>200000,"bounded team-membership policy replay");
        System.out.println("ESP_TEAM_POLICY_PASS assertions="+assertions);
    }
}

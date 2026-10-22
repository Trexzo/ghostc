package dev.ghostfusion.features;

/** Read-only display formatter for the vanilla tab-list response time. */
public final class PingHudText {
    private PingHudText(){}
    /** Missing or invalid ping is hidden; untrusted large values are bounded. */
    public static String format(int millis){
        if(millis<0)return "";
        if(millis>9999)return "PING 9999+ ms";
        return "PING "+millis+" ms";
    }
}

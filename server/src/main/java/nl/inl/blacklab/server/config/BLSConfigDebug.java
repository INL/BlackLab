package nl.inl.blacklab.server.config;

import java.util.Collections;
import java.util.List;

public class BLSConfigDebug {
    /** Explicit list of debug addresses */
    List<String> addresses = Collections.emptyList();

    /** Run all local requests in debug mode */
    boolean alwaysAllowDebugInfo = false;

    public List<String> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<String> addresses) {
        this.addresses = addresses;
    }

    public boolean isDebugMode(String ip) {
        if (alwaysAllowDebugInfo) {
            return true;
        }
        return addresses.contains(ip);
    }

    public boolean isAlwaysAllowDebugInfo() {
        return alwaysAllowDebugInfo;
    }

    public void setAlwaysAllowDebugInfo(boolean alwaysAllowDebugInfo) {
        this.alwaysAllowDebugInfo = alwaysAllowDebugInfo;
    }

}
package nl.inl.blacklab.server.config;

import java.util.Collections;
import java.util.List;

public class BLSConfigDebug {
    List<String> addresses = Collections.emptyList();

    public List<String> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<String> addresses) {
        this.addresses = addresses;
    }

    public boolean isDebugMode(String ip) {
        // LEXION Change: Always allow debug info.
        return true;
//        return addresses.contains(ip);
    }
}

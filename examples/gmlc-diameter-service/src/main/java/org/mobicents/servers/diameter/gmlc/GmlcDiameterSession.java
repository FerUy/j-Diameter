package org.mobicents.servers.diameter.gmlc;

import org.mobicents.servers.diameter.gmlc.queue.data.LocationInformation;

import java.util.concurrent.ConcurrentHashMap;

public class GmlcDiameterSession {

    static ConcurrentHashMap<String, LocationInformation> activeSessions =
            new ConcurrentHashMap<String, LocationInformation>();

    public static LocationInformation getSavedSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    void saveSession(LocationInformation locationInformation) {
        activeSessions.put(locationInformation.getSessionId(), locationInformation);
    }
}

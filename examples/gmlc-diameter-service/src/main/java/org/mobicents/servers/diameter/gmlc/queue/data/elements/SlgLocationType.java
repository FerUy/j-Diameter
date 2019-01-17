package org.mobicents.servers.diameter.gmlc.queue.data.elements;

public enum SlgLocationType {

    CurrentLocation ("CURRENT_LOCATION"),
    CurrentOrLastKnownLocation ("CURRENT_OR_LAST_KNOWN_LOCATION"),
    InitialLocation ("INITIAL_LOCATION"),
    ActivateDeferredLocation ("ACTIVATE_DEFERRED_LOCATION"),
    CancelDeferredLocation ("CANCEL_DEFERRED_LOCATION"),
    NotificationVerificationOnly ("NOTIFICATION_VERIFICATION_ONLY");

    String slgLocationType;

    SlgLocationType(String slgLocationType) {
        this.slgLocationType = slgLocationType;
    }

    public static SlgLocationType fromString(String slgLocationType) throws Exception {

        for (SlgLocationType value : SlgLocationType.values()) {
            if (value.slgLocationType.equals(slgLocationType)) {
                return value;
            }
        }
        throw new Exception("Value '" + slgLocationType + "' not defined for SlgLocationType");
    }
}

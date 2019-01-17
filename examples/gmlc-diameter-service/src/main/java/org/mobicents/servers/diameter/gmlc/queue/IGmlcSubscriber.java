package org.mobicents.servers.diameter.gmlc.queue;

import org.mobicents.servers.diameter.gmlc.queue.data.LocationInformation;

public interface IGmlcSubscriber {

    void notificationReceived(LocationInformation locationInformation);
}

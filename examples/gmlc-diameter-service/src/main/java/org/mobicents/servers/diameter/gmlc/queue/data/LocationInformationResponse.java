package org.mobicents.servers.diameter.gmlc.queue.data;

import org.mobicents.servers.diameter.gmlc.queue.data.elements.ServingNode;

public class LocationInformationResponse {
    // Diameter result
    public long resultCode;

    // RIA data
    public String imsi;
    public String msisdn;
    public String lmsi;
    public ServingNode servingNode;
    public ServingNode additionalServingNode;
    public String gmlcAddress;
    public String pprAddress;
    public Long riaFlags;

    // PLA data
    public byte[] locationEstimate;
    public Integer accuracyFulfilmentIndicator = 0;
    public Long ageOfLocationEstimate = 0L;
    public byte[] velocityEstimate;
    public byte[] eutranPositioningData;
    public byte[] eUtranCellGlobalIdentity;
    public byte[] geranPositioningData;
    public byte[] geranGanssPositioningData;
    public byte[] cellGlobalIdentity;
    public byte[] utranPositioningData;
    public byte[] utranGanssPositioningData;
    public byte[] utranAdditionalPositioningData;
    public byte[] serviceAreaIdentity;
    public ServingNode locationServingNode;
    public Long plaFlags;
    public byte[] esmlcCellInfoEcgi;
    public Integer esmlcCellPortionId;
    public String civicAddress;
    public Integer barometricPressure;
}
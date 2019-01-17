package org.mobicents.servers.diameter.gmlc.queue.data;

import org.mobicents.servers.diameter.gmlc.queue.data.elements.ServingNode;

public class LocationInformationReport {
    // LRR data
    public Integer locationEvent = 0;
    public String imsi = "";
    public String msisdn = "";
    public String imei = "";
    public byte[] lcsEpsClientNameString;
    public byte[] lcsEpsClientFormatIndicator;
    public byte[] locationEstimate;
    public Integer accuracyFulfilmentIndicator = 0;
    public Long ageOfLocationEstimate = 0L;
    public byte[] velocityEstimate;
    public byte[] eutranPositioningData;
    public byte[] ecgi;
    public byte[] geranPositioningData;
    public byte[] geranGanssPositioningData;
    public byte[] cellGlobalIdentity;
    public byte[] utranPositioningData;
    public byte[] utranGanssPositioningData;
    public byte[] utranAdditionalPositioningData;
    public byte[] serviceAreaIdentity;
    public Integer lcsServiceTypeId = 0;
    public Integer pseudonymIndicator = 0;
    public byte[] lcsQosClass;
    public ServingNode servingNode;
    public Long lrrFlags = 0L;
    public Integer lcsReferenceNumber = 0;
    public Integer deferredMtLrDataLocationType = 0;
    public String gmlcAddress = "";
    public Integer reportingAmount = 0;
    public Integer periodicLdrInformationReportingInterval = 0;
    public byte[] esmlcCellInfoEcgi;
    public Integer esmlcCellPortionId = 0;
    public byte[] onexrttRcid;
    public Integer delayedLocationReportedDataTerminationCause = 0;
    public ServingNode delayedLocationReportedDataServingNode;
    public String civicAddress = "";
    public Integer barometricPressure = 0;
}

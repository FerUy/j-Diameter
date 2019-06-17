package org.mobicents.servers.diameter.location.data;

import org.mobicents.servers.diameter.location.data.elements.AdditionalLocationEstimate;
import org.mobicents.servers.diameter.location.data.elements.LocationEstimate;
import org.mobicents.servers.diameter.location.data.elements.VelocityEstimate;
import org.mobicents.servers.diameter.location.data.elements.ServingNode;

/**
 * @author <a href="mailto:aferreiraguido@gmail.com"> Alejandro Ferreira Guido </a>
 * @author <a href="mailto:fernando.mendioroz@gmail.com"> Fernando Mendioroz </a>
 */
public class SubscriberElement {

    public String imsi;
    public String msisdn;
    public String lmsi;
    public ServingNode servingNode;
    public ServingNode additionalServingNode;
    public String gmlcAddress;
    public String pprAddress;
    public Long riaFlags;

    public Integer locationResult = 2001;

    public LocationEstimate locationEstimate;
    public AdditionalLocationEstimate addLocationEstimate;
    public Integer accuracyFulfilmentIndicator;
    public Long ageOfLocationEstimate;
    public VelocityEstimate velocityEstimate;
    public String eutranPositioningData;
    public String eutranCellGlobalIdentity;
    public String geranPositioningData;
    public String geranGanssPositioningData;
    public String cellGlobalIdentity;
    public String utranPositioningData;
    public String utranGanssPositioningData;
    public String utranAdditionalPositioningData;
    public String serviceAreaIdentity;
    public Long plaFlags;
    public String esmlcCellInfoEcgi;
    public Long esmlcCellInfoCpi;
    public String civicAddress;
    public Long barometricPressure;

    public Long lrrFlags;
    public Integer deferredMtLrDataLocationType;
    public Long deferredMtLrDataTerminationCause;
    public ServingNode deferredMtLrDataServingNode;

    public String imei;
    public String lcsEpsClientNameString;
    public Integer lcsEpsClientNameFormatInd;
    public Integer pseudonymIndicator;
    public Long lcsServiceTypeId;
    public Integer lcsQosClass;
    public Long reportingAmount;
    public Long reportingInterval;
    public String oneXRttRcid;
    public Long delayedLocationDataTerminationCause;
    public ServingNode delayedLocationDataServingNode;

    public SubscriberElement(String imsi, String msisdn, String lmsi, ServingNode servingNode, ServingNode additionalServingNode,
                             String gmlcAddress, String pprAddress, Long riaFlags, Integer locationResult,
                             LocationEstimate locationEstimate, Integer accuracyFulfilmentIndicator, Long ageOfLocationEstimate,
                             VelocityEstimate velocityEstimate, String eutranPositioningData, String eutranCellGlobalIdentity,
                             String geranPositioningData, String geranGanssPositioningData, String cellGlobalIdentity,
                             String utranPositioningData, String utranGanssPositioningData, String utranAdditionalPositioningData,
                             String serviceAreaIdentity, Long plaFlags, String esmlcCellInfoEcgi, Long esmlcCellInfoCpi,
                             String civicAddress, Long barometricPressure, Long lrrFlags, Integer deferredMtLrDataLocationType,
                             Long deferredMtLrDataTerminationCause, ServingNode deferredMtLrDataServingNode, String imei,
                             String lcsEpsClientNameString, Integer lcsEpsClientNameFormatInd, Integer pseudonymIndicator,
                             Long lcsServiceTypeId, Integer lcsQosClass, Long reportingAmount, Long reportingInterval,
                             String oneXRttRcid, Long delayedLocationDataTerminationCause, ServingNode delayedLocationDataServingNode) {
        this.imsi = imsi;
        this.msisdn = msisdn;
        this.lmsi = lmsi;
        this.servingNode = servingNode;
        this.additionalServingNode = additionalServingNode;
        this.gmlcAddress = gmlcAddress;
        this.pprAddress = pprAddress;
        this.riaFlags = riaFlags;
        this.locationResult = locationResult;
        this.locationEstimate = locationEstimate;
        this.accuracyFulfilmentIndicator = accuracyFulfilmentIndicator;
        this.ageOfLocationEstimate = ageOfLocationEstimate;
        this.velocityEstimate = velocityEstimate;
        this.eutranPositioningData = eutranPositioningData;
        this.eutranCellGlobalIdentity = eutranCellGlobalIdentity;
        this.geranPositioningData = geranPositioningData;
        this.geranGanssPositioningData = geranGanssPositioningData;
        this.cellGlobalIdentity = cellGlobalIdentity;
        this.utranPositioningData = utranPositioningData;
        this.utranGanssPositioningData = utranGanssPositioningData;
        this.utranAdditionalPositioningData = utranAdditionalPositioningData;
        this.serviceAreaIdentity = serviceAreaIdentity;
        this.plaFlags = plaFlags;
        this.esmlcCellInfoEcgi = esmlcCellInfoEcgi;
        this.esmlcCellInfoCpi = esmlcCellInfoCpi;
        this.civicAddress = civicAddress;
        this.barometricPressure = barometricPressure;
        this.lrrFlags = lrrFlags;
        this.deferredMtLrDataLocationType = deferredMtLrDataLocationType;
        this.deferredMtLrDataTerminationCause = deferredMtLrDataTerminationCause;
        this.deferredMtLrDataServingNode = deferredMtLrDataServingNode;
        this.imei = imei;
        this.lcsEpsClientNameString = lcsEpsClientNameString;
        this.lcsEpsClientNameFormatInd = lcsEpsClientNameFormatInd;
        this.pseudonymIndicator = pseudonymIndicator;
        this.lcsServiceTypeId = lcsServiceTypeId;
        this.lcsQosClass = lcsQosClass;
        this.reportingAmount = reportingAmount;
        this.reportingInterval = reportingInterval;
        this.oneXRttRcid = oneXRttRcid;
        this.delayedLocationDataTerminationCause = delayedLocationDataTerminationCause;
        this.delayedLocationDataServingNode = delayedLocationDataServingNode;
    }
}
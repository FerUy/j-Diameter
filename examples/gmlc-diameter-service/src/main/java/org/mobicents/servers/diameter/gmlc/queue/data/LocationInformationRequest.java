package org.mobicents.servers.diameter.gmlc.queue.data;

public class LocationInformationRequest {
    public String imsi = "748026871012345";                             // plrUserName
    public String msisdn = "59899077937";                               // plrMsisdn
    public String slgLocationType = "CURRENT_LOCATION";                 // plrSlgLocationType
    public String imei = "01171400466105";                              // plrImei
    public String lcsNameString = "trf";                                // plrLcsNameString
    public Integer lcsFormatIndicator = 0;                              // plrLcsFormatInd
    public Integer slgClientType = 0;                                   // plrLcsClientType
    public String requestorId = "gmlcRequestor1";                       // plrLcsRequestorIdString
    public Integer requestorFormatInd = 0;                              // plrLcsRequestorFormatIndicator
    public Long lcsPriority = 0L;                                       // plrLcsPriority
    public Integer lcsQoSClass = 1;                                     // plrQoSClass
    public Long horizontalAccuracy = 500L;                              // plrHorizontalAccuracy
    public Long verticalAccuracy = 10000L;                              // plrVerticalAccuracy
    public Integer vertCoordinateRequest = 1;                           // plrVerticalRequested
    public Integer responseTime = 0;                                    // plrResponseTime
    public Integer velocityRequested = 1;                               // plrVelocityRequested
    public Long supportedGADShapes = 6L;                                // plrLcsSupportedGadShapes
    public Long lcsServiceTypeId = 0L;                                  // plrLcsServiceTypeId
    public String lcsCodeword = "U3C0d3w0rd";                           // plrLcsCodeword
    public Integer lcsPrivacyCheckNonSession = 0;                       // plrPrivacyCheckNonSession
    public Integer lcsPrivacyCheckSession = 3;                          // plrPrivacyCheckSession
    public String apn = "trg.org";                                      // plrServiceSelection
    public Long lcsDeferredLocationType = 3L;                           // plrDeferredLocationType
    public Long plrFlags = 0L;                                          // plrFlags
    public Integer slgLcsReferenceNumber = 3;                           // plrLcsReferenceNumber
    public String gmlcAddress;
    public String lcsAreaId = "101";                                    // plrAreaIdentification
    public String lcsAdditionalAreaId = "";                             // plrAdditionalAreaIdentification
    public Long lcsAreaType = 2L;                                       // plrAreaType
    public Long lcsAdditionalAreaType = 6L;                             // plrAdditionalAreaType
    public Long lcsAreaEventIntervalTime = 600L;                        // plrAreaEventIntervalTime
    public Integer lcsAreaEventOccurrenceInfo = 1;                      // plrAreaEventOccurrenceInfo
    public Long lcsAreaEventSamplingInterval = 3600L;                   // plrAreaEventSamplingInterval
    public Long lcsAreaEventMaxInterval = 86400L;                       // plrAreaEventMaxInterval
    public Long lcsAreaEventReportingDuration = 8640000L;               // plrAreaEventReportingDuration
    public Long lcsAreaEventReportLocationReqs = 0L;                    // plrAreaEventRepLocRequirements
    public Long lcsPeriodicReportingAmount = 8639999L;                  // plrPeriodicLDRReportingAmount
    public Long lcsPeriodicReportingInterval = 8639999L;                // plrPeriodicLDRReportingInterval
    public String lcsVisitedPlmnId = "598";
    public Integer lcsPeriodicLocationSupportIndicator = 1;             // plrPeriodicLocationSupportIndicator
    public Integer lcsPrioritizedListIndicator = 0;                     // plrPrioritizedListIndicator
    public Integer lcsMotionEventOccurrenceInfo = 0;                    // plrMotionEventOccurrenceInfo
    public Long lcsMotionEventLinearDistance = 10000L;                  // plrMotionEventlinearDistance
    public Long lcsMotionEventSamplingInterval = 32767L;                // plrMotionEventSamplingInterval
    public Long lcsMotionEventIntervalTime = 3600L;                     // plrMotionEventIntervalTime
    public Long lcsMotionEventMaxInterval = 86400L;                     // plrMotionEventMaximumInterval
    public Long lcsMotionEventReportingDuration = 8640000L;             // plrMotionEvenReportingDuration
    public Long lcsMotionEventReportLocationReqs = 0L;                  // plrMotionEvenReportingLocationRequirements
    public String lcsCallbackUrl = "http://localhost:8081/api/report";  // lrrCallbackUrl

}

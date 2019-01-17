package org.mobicents.servers.diameter.gmlc.points;

import org.jdiameter.api.Answer;
import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.Avp;
import org.jdiameter.api.AvpSet;
import org.jdiameter.api.EventListener;
import org.jdiameter.api.IllegalDiameterStateException;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.NetworkReqListener;
import org.jdiameter.api.OverloadException;
import org.jdiameter.api.Request;
import org.jdiameter.api.ResultCode;
import org.jdiameter.api.RouteException;
import org.jdiameter.api.slg.ClientSLgSession;
import org.jdiameter.api.slg.events.LocationReportAnswer;
import org.jdiameter.api.slg.events.LocationReportRequest;
import org.jdiameter.api.slg.events.ProvideLocationAnswer;
import org.jdiameter.api.slg.events.ProvideLocationRequest;
import org.jdiameter.client.api.ISessionFactory;
import org.jdiameter.client.impl.app.slg.SLgClientSessionImpl;
import org.jdiameter.common.impl.app.slg.LocationReportAnswerImpl;
import org.jdiameter.common.impl.app.slg.ProvideLocationRequestImpl;
import org.jdiameter.common.impl.app.slg.SLgSessionFactoryImpl;
import org.mobicents.servers.diameter.gmlc.GmlcDiameterService;
import org.mobicents.servers.diameter.gmlc.GmlcDiameterSession;
import org.mobicents.servers.diameter.gmlc.queue.IGmlcSubscriber;
import org.mobicents.servers.diameter.gmlc.queue.data.LocationInformation;

import org.mobicents.servers.diameter.gmlc.queue.data.LocationInformationReport;
import org.mobicents.servers.diameter.gmlc.queue.data.LocationInformationRequest;
import org.mobicents.servers.diameter.gmlc.queue.data.LocationInformationResponse;
import org.mobicents.servers.diameter.gmlc.queue.data.elements.ServingNode;
import org.mobicents.servers.diameter.gmlc.queue.data.elements.SlgLocationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SLgReferencePoint extends SLgSessionFactoryImpl implements NetworkReqListener, EventListener<Request, Answer> {

    private static final Logger logger = LoggerFactory.getLogger(SLgReferencePoint.class);

    private static final int DIAMETER_ERROR_USER_UNKNOWN = 5001;
    private static final int DIAMETER_ERROR_UNAUTHORIZED_REQUESTING_NETWORK = 5490;

    private static final int DIAMETER_ERROR_UNREACHABLE_USER = 4221;
    private static final int DIAMETER_ERROR_SUSPENDED_USER = 4222;
    private static final int DIAMETER_ERROR_DETACHED_USER = 4223;
    private static final int DIAMETER_ERROR_POSITIONING_DENIED = 4224;
    private static final int DIAMETER_ERROR_POSITIONING_FAILED = 4225;
    private static final int DIAMETER_ERROR_UNKNOWN_UNREACHABLE = 4226;

    private static final int DIAMETER_AVP_DELAYED_LOCATION_REPORTING_DATA = 2555;

    private static final Object[] EMPTY_ARRAY = new Object[]{};

    IGmlcSubscriber subscriber = null;

    public SLgReferencePoint(IGmlcSubscriber subscriber) throws Exception {
        super();
        this.subscriber = subscriber;
    }

    public Answer processRequest(Request request) {
        if (logger.isInfoEnabled()) {
            logger.info("<< Received SLg request [" + request + "]");
        }

        try {
            ApplicationId slgAppId = ApplicationId.createByAuthAppId(0, this.getApplicationId());
            SLgClientSessionImpl session = sessionFactory.getNewAppSession(request.getSessionId(), slgAppId, ClientSLgSession.class, EMPTY_ARRAY);
            session.processRequest(request);
        } catch (InternalException e) {
            logger.error(">< Failure handling SLg received request [" + request + "]", e);
        }

        return null;
    }

    public void receivedSuccessMessage(Request request, Answer answer) {
        if (logger.isInfoEnabled()) {
            logger.info("<< Received SLg message for request [" + request + "] and answer [" + answer + "]");
        }
    }

    public void timeoutExpired(Request request) {
        if (logger.isInfoEnabled()) {
            logger.info("<< Received SLg timeout for request [" + request + "]");
        }
    }

    /**
     * Provide Location
     */
    public void performProvideLocationRequest(LocationInformation locationInformation)
            throws InternalException, RouteException, OverloadException, IllegalDiameterStateException {

        if (logger.isInfoEnabled()) {
            logger.info("<> Generating [PLR] Provide-Location-Request data for sending to MME");
        }

        ClientSLgSession session = ((ISessionFactory) this.sessionFactory).getNewAppSession(locationInformation.getSessionId(),
                ApplicationId.createByAuthAppId(10415, this.getApplicationId()), ClientSLgSession.class, null);

        ProvideLocationRequest plr = new ProvideLocationRequestImpl(session.getSessions().get(0).createRequest(ProvideLocationRequest.code,
                ApplicationId.createByAuthAppId(10415, this.getApplicationId()), "gmlc.beconnect.us"));

        AvpSet plrAvpSet = plr.getMessage().getAvps();
        LocationInformationRequest locationInformationRequest = locationInformation.getRequestParameters();

        try {
            if (locationInformationRequest.slgLocationType != null)
                plrAvpSet.addAvp(Avp.SLG_LOCATION_TYPE, SlgLocationType.fromString(locationInformationRequest.slgLocationType).ordinal(), 10415, true, false);
            if (locationInformationRequest.imsi != null)
                plrAvpSet.addAvp(Avp.USER_NAME, locationInformationRequest.imsi, 10415, true, false, false);
            if (locationInformationRequest.msisdn != null)
                plrAvpSet.addAvp(Avp.MSISDN, locationInformationRequest.msisdn, 10415, true, false, true);
            if (locationInformationRequest.imei != null)
                plrAvpSet.addAvp(Avp.TGPP_IMEI, locationInformationRequest.imei, 10415, true, false, false);

            AvpSet lcsEpsClientNameAvpSet = plrAvpSet.addGroupedAvp(Avp.LCS_EPS_CLIENT_NAME, 10415, true, false);
            if (locationInformationRequest.lcsNameString != null)
                lcsEpsClientNameAvpSet.addAvp(Avp.LCS_NAME_STRING, locationInformationRequest.lcsNameString, 10415, true, false, false);
            if (locationInformationRequest.lcsFormatIndicator != null)
                lcsEpsClientNameAvpSet.addAvp(Avp.LCS_FORMAT_INDICATOR, locationInformationRequest.lcsFormatIndicator, 10415, false, false, true);

            if (locationInformationRequest.slgClientType != null)
                plrAvpSet.addAvp(Avp.LCS_CLIENT_TYPE, locationInformationRequest.slgClientType, 10415, false, false, true);

            AvpSet lcsRequestorNameAvpSet = plrAvpSet.addGroupedAvp(Avp.LCS_REQUESTOR_NAME, 10415, false, false);
            if (locationInformationRequest.requestorId != null)
                lcsRequestorNameAvpSet.addAvp(Avp.LCS_NAME_STRING, locationInformationRequest.requestorId, 10415, false, false, false);
            if (locationInformationRequest.requestorFormatInd != null)
                lcsRequestorNameAvpSet.addAvp(Avp.LCS_FORMAT_INDICATOR, locationInformationRequest.requestorFormatInd, 10415, false, false, true);

            if (locationInformationRequest.lcsPriority != null)
                plrAvpSet.addAvp(Avp.LCS_PRIORITY, locationInformationRequest.lcsPriority, 10415, true, false, true);

            AvpSet lcsQosNameAvpSet = plrAvpSet.addGroupedAvp(Avp.LCS_QOS, 10415, true, false);
            if (locationInformationRequest.lcsQoSClass != null)
                lcsQosNameAvpSet.addAvp(Avp.LCS_QOS_CLASS, locationInformationRequest.lcsQoSClass, 10415, true, false, true);
            lcsQosNameAvpSet.addAvp(Avp.HORIZONTAL_ACCURACY, locationInformationRequest.horizontalAccuracy, 10415, true, false, true);
            lcsQosNameAvpSet.addAvp(Avp.VERTICAL_ACCURACY, locationInformationRequest.verticalAccuracy, 10415, true, false, true);
            lcsQosNameAvpSet.addAvp(Avp.VERTICAL_REQUESTED, locationInformationRequest.vertCoordinateRequest, 10415, true, false, true);
            lcsQosNameAvpSet.addAvp(Avp.RESPONSE_TIME, locationInformationRequest.responseTime, 10415, false, true, true);

            plrAvpSet.addAvp(Avp.VELOCITY_REQUESTED, locationInformationRequest.velocityRequested, 10415, true, false, true);
            plrAvpSet.addAvp(Avp.LCS_SUPPORTED_GAD_SHAPES, locationInformationRequest.supportedGADShapes, 10415, true, false, true);
            plrAvpSet.addAvp(Avp.LCS_SERVICE_TYPE_ID, locationInformationRequest.lcsServiceTypeId, 10415, true, false, true);
            plrAvpSet.addAvp(Avp.LCS_CODEWORD, locationInformationRequest.lcsCodeword, 10415, true, false, false);
            plrAvpSet.addAvp(Avp.LCS_PRIVACY_CHECK_SESSION, locationInformationRequest.lcsPrivacyCheckSession, 10415, true, false, true);
            plrAvpSet.addAvp(Avp.LCS_PRIVACY_CHECK_NON_SESSION, locationInformationRequest.lcsPrivacyCheckNonSession, 10415, true, false, true);
            plrAvpSet.addAvp(Avp.SERVICE_SELECTION, locationInformationRequest.apn, 10415, true, false, true);
            plrAvpSet.addAvp(Avp.DEFERRED_LOCATION_TYPE, locationInformationRequest.lcsDeferredLocationType, 10415, false, false, true);
            plrAvpSet.addAvp(Avp.LCS_REFERENCE_NUMBER, locationInformationRequest.slgLcsReferenceNumber, 10415, false, false, false);

            AvpSet areaEventInfoAvpSet = plrAvpSet.addGroupedAvp(Avp.AREA_EVENT_INFO, 10415, false, false);
            AvpSet areaDefinition = plrAvpSet.addGroupedAvp(Avp.AREA_DEFINITION, 10415, false, false);
            AvpSet area = plrAvpSet.addGroupedAvp(Avp.AREA, 10415, false, false);
            area.addAvp(Avp.AREA_TYPE, locationInformationRequest.lcsAreaType,10415, false, false, true);
            area.addAvp(Avp.AREA_IDENTIFICATION, locationInformationRequest.lcsAreaId,10415, false, false, false);
            AvpSet additionalArea = plrAvpSet.addGroupedAvp(Avp.ADDITIONAL_AREA, 10415, false, false);
            additionalArea.addAvp(Avp.AREA_TYPE, locationInformationRequest.lcsAdditionalAreaType, 10415, false, false, true);
            additionalArea.addAvp(Avp.AREA_IDENTIFICATION, locationInformationRequest.lcsAdditionalAreaId,10415, false, false, false);
            areaDefinition.addAvp(Avp.AREA, 10415, false, false);
            areaDefinition.addAvp(Avp.ADDITIONAL_AREA, 10415, false, false);
            areaEventInfoAvpSet.addAvp(Avp.AREA_DEFINITION,10415, false, false);
            areaEventInfoAvpSet.addAvp(Avp.OCCURRENCE_INFO, locationInformationRequest.lcsAreaEventOccurrenceInfo,10415, false, false);
            areaEventInfoAvpSet.addAvp(Avp.INTERVAL_TIME, locationInformationRequest.lcsAreaEventIntervalTime,10415, false, false, true);
            areaEventInfoAvpSet.addAvp(Avp.MAXIMUM_INTERVAL, locationInformationRequest.lcsAreaEventMaxInterval,10415, false, false, true);
            areaEventInfoAvpSet.addAvp(Avp.SAMPLING_INTERVAL, locationInformationRequest.lcsAreaEventSamplingInterval,10415, false, false, true);
            areaEventInfoAvpSet.addAvp(Avp.REPORTING_DURATION, locationInformationRequest.lcsAreaEventReportingDuration,10415, false, false, true);
            areaEventInfoAvpSet.addAvp(Avp.REPORTING_LOCATION_REQUIREMENTS, locationInformationRequest.lcsAreaEventReportLocationReqs,10415, false, false, true);

            plrAvpSet.addAvp(Avp.GMLC_ADDRESS, locationInformationRequest.gmlcAddress, 10415, false, false, false);
            plrAvpSet.addAvp(Avp.PLR_FLAGS, locationInformationRequest.plrFlags, 10415, false, false, true);

            AvpSet periodicLdrInformationAvpSet = plrAvpSet.addGroupedAvp(Avp.PERIODIC_LDR_INFORMATION, 10415, false, false);
            periodicLdrInformationAvpSet.addAvp(Avp.REPORTING_AMOUNT, locationInformationRequest.lcsPeriodicReportingAmount, 10415, false, false, true);
            periodicLdrInformationAvpSet.addAvp(Avp.REPORTING_INTERVAL, locationInformationRequest.lcsPeriodicReportingInterval, 10415, false, false, true);

            AvpSet reportingPlmnListAvpSet = plrAvpSet.addGroupedAvp(Avp.REPORTING_PLMN_LIST, 10415, false, false);
            AvpSet plmnIdListAvpSet = reportingPlmnListAvpSet.addGroupedAvp(Avp.PLMN_ID_LIST, 10415, false, false);  // maximun 20 elements
            plmnIdListAvpSet.addAvp(Avp.VISITED_PLMN_ID, locationInformationRequest.lcsVisitedPlmnId, 10415, true, false, true);
            plmnIdListAvpSet.addAvp(Avp.PERIODIC_LOCATION_SUPPORT_INDICATOR, locationInformationRequest.lcsPeriodicLocationSupportIndicator, 10415, false, false, true);
            reportingPlmnListAvpSet.addAvp(Avp.PRIORITIZED_LIST_INDICATOR, locationInformationRequest.lcsPrioritizedListIndicator, 10415, false, false, true);

            AvpSet motionEventInfoAvpSet = plrAvpSet.addGroupedAvp(Avp.MOTION_EVENT_INFO);
            motionEventInfoAvpSet.addAvp(Avp.LINEAR_DISTANCE, locationInformationRequest.lcsMotionEventLinearDistance, 10415, false, false, true);
            motionEventInfoAvpSet.addAvp(Avp.OCCURRENCE_INFO, locationInformationRequest.lcsMotionEventOccurrenceInfo, 10415, false, false, true);
            motionEventInfoAvpSet.addAvp(Avp.INTERVAL_TIME, locationInformationRequest.lcsMotionEventIntervalTime, 10415, false, false, true);
            motionEventInfoAvpSet.addAvp(Avp.MAXIMUM_INTERVAL, locationInformationRequest.lcsMotionEventMaxInterval, 10415, false, false, true);
            motionEventInfoAvpSet.addAvp(Avp.SAMPLING_INTERVAL, locationInformationRequest.lcsMotionEventSamplingInterval, 10415, false, false, true);
            motionEventInfoAvpSet.addAvp(Avp.REPORTING_DURATION, locationInformationRequest.lcsMotionEventReportingDuration, 10415, false, false, true);
            motionEventInfoAvpSet.addAvp(Avp.REPORTING_LOCATION_REQUIREMENTS, locationInformationRequest.lcsMotionEventReportLocationReqs, 10415, false, false, true);

            /*
            AvpSet supportedFeaturesAvpSet = plrAvpSet.addGroupedAvp(Avp.SUPPORTED_FEATURES);
            supportedFeaturesAvpSet.addAvp(Avp.VENDOR_ID, locationInformationRequest.plrFlags, true);
            supportedFeaturesAvpSet.addAvp(Avp.FEATURE_LIST_ID, locationInformationRequest.plrFlags, true);
            supportedFeaturesAvpSet.addAvp(Avp.FEATURE_LIST, locationInformationRequest.plrFlags, true);
            */

        } catch (Exception e) {
            locationInformation.createResponse(LocationInformation.LocationPrimitive.LocationAnswerError);
            subscriber.notificationReceived(locationInformation);
        }

        session.sendProvideLocationRequest(plr);
    }

    @Override
    public void doProvideLocationAnswerEvent(ClientSLgSession session, ProvideLocationRequest plr, ProvideLocationAnswer pla)
            throws InternalException, IllegalDiameterStateException, RouteException, OverloadException {

        LocationInformation locationInformation = GmlcDiameterSession.getSavedSession(session.getSessionId());

        if (logger.isInfoEnabled()) {
            logger.info("<> Processing [PLA] Provide-Location-Answer for request session '" + session.getSessionId() +
                    "' for location information session '" + locationInformation.getSessionId() + "'");
        }

        AvpSet plaAvpSet = pla.getMessage().getAvps();

        LocationInformationResponse locationInformationResponse =
                locationInformation.createResponse(LocationInformation.LocationPrimitive.ProvideLocationAnswer);

        try {
            locationInformationResponse.resultCode = plaAvpSet.getAvp(Avp.RESULT_CODE).getUnsigned32();

            if (plaAvpSet.getAvp(Avp.LOCATION_ESTIMATE) != null)
                locationInformationResponse.locationEstimate = plaAvpSet.getAvp(Avp.LOCATION_ESTIMATE).getOctetString();
            if (plaAvpSet.getAvp(Avp.ACCURACY_FULFILMENT_INDICATOR) != null)
                locationInformationResponse.accuracyFulfilmentIndicator = plaAvpSet.getAvp(Avp.ACCURACY_FULFILMENT_INDICATOR).getInteger32();
            if (plaAvpSet.getAvp(Avp.AGE_OF_LOCATION_ESTIMATE) != null)
                locationInformationResponse.ageOfLocationEstimate = plaAvpSet.getAvp(Avp.AGE_OF_LOCATION_ESTIMATE).getUnsigned32();
            if (plaAvpSet.getAvp(Avp.VELOCITY_ESTIMATE) != null)
                locationInformationResponse.velocityEstimate = plaAvpSet.getAvp(Avp.VELOCITY_ESTIMATE).getOctetString();
            if (plaAvpSet.getAvp(Avp.EUTRAN_POSITIONING_DATA) != null)
                locationInformationResponse.eutranPositioningData = plaAvpSet.getAvp(Avp.EUTRAN_POSITIONING_DATA).getOctetString();
            if (plaAvpSet.getAvp(Avp.ECGI) != null)
                locationInformationResponse.eUtranCellGlobalIdentity = plaAvpSet.getAvp(Avp.ECGI).getOctetString();

            if (plaAvpSet.getAvp(Avp.GERAN_POSITIONING_INFO) != null) {
                AvpSet geranPositioningInfo = plaAvpSet.getAvp(Avp.GERAN_POSITIONING_INFO).getGrouped();
                if (geranPositioningInfo.getAvp(Avp.GERAN_POSITIONING_DATA) != null)
                    locationInformationResponse.geranPositioningData = geranPositioningInfo.getAvp(Avp.GERAN_POSITIONING_DATA).getOctetString();
                ;
                if (geranPositioningInfo.getAvp(Avp.GERAN_GANSS_POSITIONING_DATA) != null)
                    locationInformationResponse.geranGanssPositioningData = geranPositioningInfo.getAvp(Avp.GERAN_GANSS_POSITIONING_DATA).getOctetString();
                ;
            }

            if (plaAvpSet.getAvp(Avp.CELL_GLOBAL_IDENTITY) != null)
                locationInformationResponse.cellGlobalIdentity = plaAvpSet.getAvp(Avp.CELL_GLOBAL_IDENTITY).getOctetString();

            if (plaAvpSet.getAvp(Avp.UTRAN_POSITIONING_INFO) != null) {
                AvpSet utranPositioningInfo = plaAvpSet.getAvp(Avp.UTRAN_POSITIONING_INFO).getGrouped();
                locationInformationResponse.utranPositioningData = utranPositioningInfo.getAvp(Avp.UTRAN_POSITIONING_DATA).getOctetString();
                locationInformationResponse.utranGanssPositioningData = utranPositioningInfo.getAvp(Avp.UTRAN_GANSS_POSITIONING_DATA).getOctetString();
                locationInformationResponse.utranAdditionalPositioningData = utranPositioningInfo.getAvp(Avp.UTRAN_ADDITIONAL_POSITIONING_DATA).getOctetString();
            }

            if (plaAvpSet.getAvp(Avp.SERVICE_AREA_IDENTITY) != null)
                locationInformationResponse.serviceAreaIdentity = plaAvpSet.getAvp(Avp.SERVICE_AREA_IDENTITY).getOctetString();

            if (plaAvpSet.getAvp(Avp.SERVING_NODE) != null) {
                AvpSet servingNodeAvpSet = plaAvpSet.getAvp(Avp.SERVING_NODE).getGrouped();
                locationInformationResponse.locationServingNode = new ServingNode();

                if (servingNodeAvpSet.getAvp(Avp.SGSN_NUMBER) != null)
                    locationInformationResponse.locationServingNode.sgsnNumber = servingNodeAvpSet.getAvp(Avp.SGSN_NUMBER).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.SGSN_NAME) != null)
                    locationInformationResponse.locationServingNode.sgsnName = servingNodeAvpSet.getAvp(Avp.SGSN_NAME).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.SGSN_REALM) != null)
                    locationInformationResponse.locationServingNode.sgsnRealm = servingNodeAvpSet.getAvp(Avp.SGSN_REALM).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.MME_NAME) != null)
                    locationInformationResponse.locationServingNode.mmeName = servingNodeAvpSet.getAvp(Avp.MME_NAME).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.MME_REALM) != null)
                    locationInformationResponse.locationServingNode.mmeRealm = servingNodeAvpSet.getAvp(Avp.MME_REALM).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.MSC_NUMBER) != null)
                    locationInformationResponse.locationServingNode.mscNumber = servingNodeAvpSet.getAvp(Avp.MSC_NUMBER).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.TGPP_AAA_SERVER_NAME) != null)
                    locationInformationResponse.locationServingNode.tgppAAAServerName = servingNodeAvpSet.getAvp(Avp.TGPP_AAA_SERVER_NAME).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.LCS_CAPABILITIES_SETS) != null)
                    locationInformationResponse.locationServingNode.lcsCapabilitySets = servingNodeAvpSet.getAvp(Avp.LCS_CAPABILITIES_SETS).getInteger32();
                if (servingNodeAvpSet.getAvp(Avp.GMLC_ADDRESS) != null)
                    locationInformationResponse.locationServingNode.gmlcAddress = servingNodeAvpSet.getAvp(Avp.GMLC_ADDRESS).getUTF8String();
            }

            if (plaAvpSet.getAvp(Avp.PLA_FLAGS) != null)
                locationInformationResponse.plaFlags = plaAvpSet.getAvp(Avp.PLA_FLAGS).getUnsigned32();

            if (plaAvpSet.getAvp(Avp.ESMLC_CELL_INFO) != null) {
                AvpSet esmlcCellInfo = plaAvpSet.getAvp(Avp.ESMLC_CELL_INFO).getGrouped();
                if (esmlcCellInfo.getAvp(Avp.ECGI) != null)
                    locationInformationResponse.esmlcCellInfoEcgi = esmlcCellInfo.getAvp(Avp.ECGI).getOctetString();
                if (esmlcCellInfo.getAvp(Avp.CELL_PORTION_ID) != null)
                    locationInformationResponse.esmlcCellPortionId = esmlcCellInfo.getAvp(Avp.CELL_PORTION_ID).getInteger32();
            }

            if (plaAvpSet.getAvp(Avp.CIVIC_ADDRESS) != null)
                locationInformationResponse.civicAddress = plaAvpSet.getAvp(Avp.CIVIC_ADDRESS).getUTF8String();
            if (plaAvpSet.getAvp(Avp.BAROMETRIC_PRESSURE) != null)
                locationInformationResponse.barometricPressure = plaAvpSet.getAvp(Avp.BAROMETRIC_PRESSURE).getInteger32();

            /*if (plaAvpSet.addGroupedAvp(Avp.SUPPORTED_FEATURES) != null) {
                AvpSet supportedFeaturesAvpSet = plaAvpSet.getAvp(Avp.SUPPORTED_FEATURES).getGrouped();
                if (supportedFeaturesAvpSet.getAvp(Avp.VENDOR_ID) != null)
                    locationInformationResponse.supportedFeaturesVendorId = supportedFeaturesAvpSet.getAvp(Avp.VENDOR_ID).getInteger32();
                if (supportedFeaturesAvpSet.getAvp(Avp.FEATURE_LIST_ID) != null)
                    locationInformationResponse.supportedFeaturesListId = supportedFeaturesAvpSet.getAvp(Avp.FEATURE_LIST_ID).getInteger32();
                if (supportedFeaturesAvpSet.getAvp(Avp.FEATURE_LIST) != null)
                    locationInformationResponse.supportedFeaturesList = supportedFeaturesAvpSet.getAvp(Avp.FEATURE_LIST).getInteger32();
            }*/
        } catch (Exception e) {
            locationInformation.createResponse(LocationInformation.LocationPrimitive.LocationAnswerError);
            logger.error("*** Error processiong [PLA] Provide-Location-Answer parameters!", e);
        }

        subscriber.notificationReceived(locationInformation);
    }

    /**
     * Location Report
     */
    @Override
    public void doLocationReportRequestEvent(ClientSLgSession session, LocationReportRequest lrr)
            throws InternalException, IllegalDiameterStateException, RouteException, OverloadException {

        if (logger.isInfoEnabled()) {
            logger.info("<> Received [LRR] Location-Report-Request");
        }

        AvpSet lrrAvpSet = lrr.getMessage().getAvps();

        LocationInformation locationInformation = new LocationInformation(LocationInformation.LocationPrimitive.LocationReportRequest);
        LocationInformationReport locationInformationReport = locationInformation.getReportParameters();

        try {
            if (lrrAvpSet.getAvp(Avp.LOCATION_EVENT) != null)
                locationInformationReport.locationEvent = lrrAvpSet.getAvp(Avp.LOCATION_EVENT).getInteger32();
            if (lrrAvpSet.getAvp(Avp.USER_NAME) != null)
                locationInformationReport.imsi = lrrAvpSet.getAvp(Avp.USER_NAME).getOctetString().toString();
            if (lrrAvpSet.getAvp(Avp.MSISDN) != null)
                locationInformationReport.msisdn = lrrAvpSet.getAvp(Avp.MSISDN).getOctetString().toString();
            if (lrrAvpSet.getAvp(Avp.TGPP_IMEI) != null)
                locationInformationReport.imei = lrrAvpSet.getAvp(Avp.TGPP_IMEI).getOctetString().toString();

            if (lrrAvpSet.getAvp(Avp.LCS_EPS_CLIENT_NAME) != null) {
                AvpSet lcsEpsClientNameAvpSet = lrrAvpSet.getAvp(Avp.LCS_EPS_CLIENT_NAME).getGrouped();
                locationInformationReport.lcsEpsClientNameString = lcsEpsClientNameAvpSet.getAvp(Avp.LCS_NAME_STRING).getOctetString();
                locationInformationReport.lcsEpsClientFormatIndicator = lcsEpsClientNameAvpSet.getAvp(Avp.LCS_FORMAT_INDICATOR).getOctetString();
            }

            if (lrrAvpSet.getAvp(Avp.LOCATION_ESTIMATE) != null)
                locationInformationReport.locationEstimate = lrrAvpSet.getAvp(Avp.LOCATION_ESTIMATE).getOctetString();
            if (lrrAvpSet.getAvp(Avp.ACCURACY_FULFILMENT_INDICATOR) != null)
                locationInformationReport.accuracyFulfilmentIndicator = lrrAvpSet.getAvp(Avp.ACCURACY_FULFILMENT_INDICATOR).getInteger32();
            if (lrrAvpSet.getAvp(Avp.AGE_OF_LOCATION_ESTIMATE) != null)
                locationInformationReport.ageOfLocationEstimate = lrrAvpSet.getAvp(Avp.AGE_OF_LOCATION_ESTIMATE).getUnsigned32();
            if (lrrAvpSet.getAvp(Avp.VELOCITY_ESTIMATE) != null)
                locationInformationReport.velocityEstimate = lrrAvpSet.getAvp(Avp.VELOCITY_ESTIMATE).getOctetString();
            if (lrrAvpSet.getAvp(Avp.EUTRAN_POSITIONING_DATA) != null)
                locationInformationReport.eutranPositioningData = lrrAvpSet.getAvp(Avp.EUTRAN_POSITIONING_DATA).getOctetString();
            if (lrrAvpSet.getAvp(Avp.ECGI) != null)
                locationInformationReport.ecgi = lrrAvpSet.getAvp(Avp.ECGI).getOctetString();

            if (lrrAvpSet.getAvp(Avp.GERAN_POSITIONING_INFO) != null) {
                AvpSet geranPositioningInfo = lrrAvpSet.getAvp(Avp.GERAN_POSITIONING_INFO).getGrouped();
                locationInformationReport.geranPositioningData = geranPositioningInfo.getAvp(Avp.GERAN_POSITIONING_DATA).getOctetString();
                locationInformationReport.geranGanssPositioningData = geranPositioningInfo.getAvp(Avp.GERAN_GANSS_POSITIONING_DATA).getOctetString();
            }

            if (lrrAvpSet.getAvp(Avp.CELL_GLOBAL_IDENTITY) != null)
                locationInformationReport.cellGlobalIdentity = lrrAvpSet.getAvp(Avp.CELL_GLOBAL_IDENTITY).getOctetString();

            if (lrrAvpSet.getAvp(Avp.UTRAN_POSITIONING_INFO) != null) {
                AvpSet utranPositioningInfo = lrrAvpSet.getAvp(Avp.UTRAN_POSITIONING_INFO).getGrouped();
                locationInformationReport.utranPositioningData = utranPositioningInfo.getAvp(Avp.UTRAN_POSITIONING_DATA).getOctetString();
                locationInformationReport.utranGanssPositioningData = utranPositioningInfo.getAvp(Avp.UTRAN_GANSS_POSITIONING_DATA).getOctetString();
                locationInformationReport.utranAdditionalPositioningData = utranPositioningInfo.getAvp(Avp.UTRAN_ADDITIONAL_POSITIONING_DATA).getOctetString();
            }

            if (lrrAvpSet.getAvp(Avp.SERVICE_AREA_IDENTITY) != null)
                locationInformationReport.serviceAreaIdentity = lrrAvpSet.getAvp(Avp.SERVICE_AREA_IDENTITY).getOctetString();

            if (lrrAvpSet.getAvp(Avp.LCS_SERVICE_TYPE_ID) != null)
                locationInformationReport.lcsServiceTypeId = lrrAvpSet.getAvp(Avp.LCS_SERVICE_TYPE_ID).getInteger32();
            if (lrrAvpSet.getAvp(Avp.PSEUDONYM_INDICATOR) != null)
                locationInformationReport.pseudonymIndicator = lrrAvpSet.getAvp(Avp.PSEUDONYM_INDICATOR).getInteger32();

            /*if (lrrAvpSet.getAvp(Avp.SUPPORTED_FEATURES) != null) {
                AvpSet supportedFeaturesAvpSet = lrrAvpSet.getAvp(Avp.SUPPORTED_FEATURES).getGrouped();
                if (supportedFeaturesAvpSet.getAvp(Avp.VENDOR_ID) != null)
                    locationInformationResponse.supportedFeaturesVendorId = supportedFeaturesAvpSet.getAvp(Avp.VENDOR_ID).getInteger32();
                if (supportedFeaturesAvpSet.getAvp(Avp.FEATURE_LIST_ID) != null)
                    locationInformationResponse.supportedFeaturesListId = supportedFeaturesAvpSet.getAvp(Avp.FEATURE_LIST_ID).getInteger32();
                if (supportedFeaturesAvpSet.getAvp(Avp.FEATURE_LIST) != null)
                    locationInformationResponse.supportedFeaturesList = supportedFeaturesAvpSet.getAvp(Avp.FEATURE_LIST).getInteger32();
            }*/

            if (lrrAvpSet.getAvp(Avp.TGPP_IMEI) != null)
                locationInformationReport.lcsQosClass = lrrAvpSet.getAvp(Avp.LCS_QOS_CLASS).getOctetString();


            if (lrrAvpSet.getAvp(Avp.SERVING_NODE) != null) {
                AvpSet servingNodeAvpSet = lrrAvpSet.getAvp(Avp.SERVING_NODE).getGrouped();
                locationInformationReport.servingNode = new ServingNode();

                if (servingNodeAvpSet.getAvp(Avp.SGSN_NUMBER) != null)
                    locationInformationReport.servingNode.sgsnNumber = servingNodeAvpSet.getAvp(Avp.SGSN_NUMBER).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.SGSN_NAME) != null)
                    locationInformationReport.servingNode.sgsnName = servingNodeAvpSet.getAvp(Avp.SGSN_NAME).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.SGSN_REALM) != null)
                    locationInformationReport.servingNode.sgsnRealm = servingNodeAvpSet.getAvp(Avp.SGSN_REALM).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.MME_NAME) != null)
                    locationInformationReport.servingNode.mmeName = servingNodeAvpSet.getAvp(Avp.MME_NAME).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.MME_REALM) != null)
                    locationInformationReport.servingNode.mmeRealm = servingNodeAvpSet.getAvp(Avp.MME_REALM).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.MSC_NUMBER) != null)
                    locationInformationReport.servingNode.mscNumber = servingNodeAvpSet.getAvp(Avp.MSC_NUMBER).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.TGPP_AAA_SERVER_NAME) != null)
                    locationInformationReport.servingNode.tgppAAAServerName = servingNodeAvpSet.getAvp(Avp.TGPP_AAA_SERVER_NAME).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.LCS_CAPABILITIES_SETS) != null)
                    locationInformationReport.servingNode.lcsCapabilitySets = servingNodeAvpSet.getAvp(Avp.LCS_CAPABILITIES_SETS).getInteger32();
                if (servingNodeAvpSet.getAvp(Avp.GMLC_ADDRESS) != null)
                    locationInformationReport.servingNode.gmlcAddress = servingNodeAvpSet.getAvp(Avp.GMLC_ADDRESS).getUTF8String();
            }

            if (lrrAvpSet.getAvp(Avp.LRR_FLAGS) != null)
                locationInformationReport.lrrFlags = lrrAvpSet.getAvp(Avp.LRR_FLAGS).getUnsigned32();
            if (lrrAvpSet.getAvp(Avp.LCS_REFERENCE_NUMBER) != null)
                locationInformationReport.lcsReferenceNumber = lrrAvpSet.getAvp(Avp.LCS_REFERENCE_NUMBER).getInteger32();

            if (lrrAvpSet.getAvp(Avp.DEFERRED_MT_LR_DATA) != null) {
                AvpSet deferredMtLrData = lrrAvpSet.getAvp(Avp.DEFERRED_MT_LR_DATA).getGrouped();
                locationInformationReport.deferredMtLrDataLocationType = deferredMtLrData.getAvp(Avp.DEFERRED_LOCATION_TYPE).getInteger32();
            }

            if (lrrAvpSet.getAvp(Avp.GMLC_ADDRESS) != null)
                locationInformationReport.gmlcAddress = lrrAvpSet.getAvp(Avp.GMLC_ADDRESS).getUTF8String();
            if (lrrAvpSet.getAvp(Avp.REPORTING_AMOUNT) != null)
                locationInformationReport.reportingAmount = lrrAvpSet.getAvp(Avp.REPORTING_AMOUNT).getInteger32();

            if (lrrAvpSet.getAvp(Avp.PERIODIC_LDR_INFORMATION) != null) {
                AvpSet deferredMtLrData = lrrAvpSet.getAvp(Avp.PERIODIC_LDR_INFORMATION).getGrouped();
                locationInformationReport.periodicLdrInformationReportingInterval = deferredMtLrData.getAvp(Avp.REPORTING_INTERVAL).getInteger32();
            }

            if (lrrAvpSet.getAvp(Avp.ESMLC_CELL_INFO) != null) {
                AvpSet esmlcCellInfo = lrrAvpSet.getAvp(Avp.ESMLC_CELL_INFO).getGrouped();
                if (esmlcCellInfo.getAvp(Avp.ECGI) != null)
                    locationInformationReport.esmlcCellInfoEcgi = esmlcCellInfo.getAvp(Avp.ECGI).getOctetString();
                if (esmlcCellInfo.getAvp(Avp.CELL_PORTION_ID) != null)
                    locationInformationReport.esmlcCellPortionId = esmlcCellInfo.getAvp(Avp.CELL_PORTION_ID).getInteger32();
            }

            if (lrrAvpSet.getAvp(Avp.TGPP_IMEI) != null)
                locationInformationReport.onexrttRcid = lrrAvpSet.getAvp(Avp.ONEXRTT_RCID).getOctetString();

            if (lrrAvpSet.getAvp(DIAMETER_AVP_DELAYED_LOCATION_REPORTING_DATA) != null) {
                AvpSet delayedLocationReportedData = lrrAvpSet.getAvp(DIAMETER_AVP_DELAYED_LOCATION_REPORTING_DATA).getGrouped();
                locationInformationReport.delayedLocationReportedDataTerminationCause = delayedLocationReportedData.getAvp(Avp.TERMINATION_CAUSE).getInteger32();

                if (delayedLocationReportedData.getAvp(Avp.SERVING_NODE) != null) {
                    AvpSet servingNodeAvpSet = delayedLocationReportedData.getAvp(Avp.SERVING_NODE).getGrouped();
                    locationInformationReport.delayedLocationReportedDataServingNode = new ServingNode();

                    if (servingNodeAvpSet.getAvp(Avp.SGSN_NUMBER) != null)
                        locationInformationReport.delayedLocationReportedDataServingNode.sgsnNumber = servingNodeAvpSet.getAvp(Avp.SGSN_NUMBER).getUTF8String();
                    if (servingNodeAvpSet.getAvp(Avp.SGSN_NAME) != null)
                        locationInformationReport.delayedLocationReportedDataServingNode.sgsnName = servingNodeAvpSet.getAvp(Avp.SGSN_NAME).getUTF8String();
                    if (servingNodeAvpSet.getAvp(Avp.SGSN_REALM) != null)
                        locationInformationReport.delayedLocationReportedDataServingNode.sgsnRealm = servingNodeAvpSet.getAvp(Avp.SGSN_REALM).getUTF8String();
                    if (servingNodeAvpSet.getAvp(Avp.MME_NAME) != null)
                        locationInformationReport.delayedLocationReportedDataServingNode.mmeName = servingNodeAvpSet.getAvp(Avp.MME_NAME).getUTF8String();
                    if (servingNodeAvpSet.getAvp(Avp.MME_REALM) != null)
                        locationInformationReport.delayedLocationReportedDataServingNode.mmeRealm = servingNodeAvpSet.getAvp(Avp.MME_REALM).getUTF8String();
                    if (servingNodeAvpSet.getAvp(Avp.MSC_NUMBER) != null)
                        locationInformationReport.delayedLocationReportedDataServingNode.mscNumber = servingNodeAvpSet.getAvp(Avp.MSC_NUMBER).getUTF8String();
                    if (servingNodeAvpSet.getAvp(Avp.TGPP_AAA_SERVER_NAME) != null)
                        locationInformationReport.delayedLocationReportedDataServingNode.tgppAAAServerName = servingNodeAvpSet.getAvp(Avp.TGPP_AAA_SERVER_NAME).getUTF8String();
                    if (servingNodeAvpSet.getAvp(Avp.LCS_CAPABILITIES_SETS) != null)
                        locationInformationReport.delayedLocationReportedDataServingNode.lcsCapabilitySets = servingNodeAvpSet.getAvp(Avp.LCS_CAPABILITIES_SETS).getInteger32();
                    if (servingNodeAvpSet.getAvp(Avp.GMLC_ADDRESS) != null)
                        locationInformationReport.delayedLocationReportedDataServingNode.gmlcAddress = servingNodeAvpSet.getAvp(Avp.GMLC_ADDRESS).getUTF8String();
                }
            }

            if (lrrAvpSet.getAvp(Avp.CIVIC_ADDRESS) != null)
                locationInformationReport.civicAddress = lrrAvpSet.getAvp(Avp.CIVIC_ADDRESS).getUTF8String();
            if (lrrAvpSet.getAvp(Avp.BAROMETRIC_PRESSURE) != null)
                locationInformationReport.barometricPressure = lrrAvpSet.getAvp(Avp.BAROMETRIC_PRESSURE).getInteger32();
        } catch (Exception e) {

        }

        subscriber.notificationReceived(locationInformation);

        LocationReportAnswer lra = new LocationReportAnswerImpl((Request) lrr.getMessage(), ResultCode.SUCCESS);

        AvpSet lraAvpSet = lrr.getMessage().getAvps();

        lraAvpSet.addAvp(Avp.GMLC_ADDRESS, locationInformationReport.gmlcAddress, true);
        lraAvpSet.addAvp(Avp.LRA_FLAGS, 0);

        /*AvpSet reportingPlmnListAvpSet = lraAvpSet.addGroupedAvp(Avp.REPORTING_PLMN_LIST);
        AvpSet plmnIdListAvpSet = reportingPlmnListAvpSet.addGroupedAvp(Avp.PLMN_ID_LIST);  // maximun 20 elements
        //TODO - plmnIdListAvpSet.addAvp(Avp.VISITED_PLMN_ID, locationInformation.getRequestParameters().???, true);
        plmnIdListAvpSet.addAvp(Avp.PERIODIC_LOCATION_SUPPORT_INDICATOR, locationInformation.getRequestParameters().lcsPeriodicLocationSupportIndicator, true);
        reportingPlmnListAvpSet.addAvp(Avp.PRIORITIZED_LIST_INDICATOR, locationInformation.getRequestParameters().lcsPrioritizedListIndicator, true);*/

        lraAvpSet.addAvp(Avp.LCS_REFERENCE_NUMBER, locationInformationReport.lcsReferenceNumber, true);

        /*if (lrrAvpSet.getAvp(Avp.SUPPORTED_FEATURES) != null) {
            AvpSet supportedFeaturesAvpSet = lrrAvpSet.getAvp(Avp.SUPPORTED_FEATURES).getGrouped();
            if (supportedFeaturesAvpSet.getAvp(Avp.VENDOR_ID) != null)
                locationInformationResponse.supportedFeaturesVendorId = supportedFeaturesAvpSet.getAvp(Avp.VENDOR_ID).getInteger32();
            if (supportedFeaturesAvpSet.getAvp(Avp.FEATURE_LIST_ID) != null)
                locationInformationResponse.supportedFeaturesListId = supportedFeaturesAvpSet.getAvp(Avp.FEATURE_LIST_ID).getInteger32();
            if (supportedFeaturesAvpSet.getAvp(Avp.FEATURE_LIST) != null)
                locationInformationResponse.supportedFeaturesList = supportedFeaturesAvpSet.getAvp(Avp.FEATURE_LIST).getInteger32();
        }*/

        session.sendLocationReportAnswer(lra);
    }

}
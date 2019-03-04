package org.mobicents.servers.diameter.location.points;

import org.jdiameter.api.Answer;
import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.Avp;
import org.jdiameter.api.AvpDataException;
import org.jdiameter.api.AvpSet;
import org.jdiameter.api.EventListener;
import org.jdiameter.api.IllegalDiameterStateException;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.NetworkReqListener;
import org.jdiameter.api.OverloadException;
import org.jdiameter.api.Request;
import org.jdiameter.api.ResultCode;
import org.jdiameter.api.RouteException;
import org.jdiameter.api.slg.ServerSLgSession;
import org.jdiameter.api.slg.events.LocationReportAnswer;
import org.jdiameter.api.slg.events.LocationReportRequest;
import org.jdiameter.api.slg.events.ProvideLocationAnswer;
import org.jdiameter.api.slg.events.ProvideLocationRequest;
import org.jdiameter.client.api.ISessionFactory;
import org.jdiameter.common.impl.app.slg.LocationReportRequestImpl;
import org.jdiameter.common.impl.app.slg.ProvideLocationAnswerImpl;
import org.jdiameter.common.impl.app.slg.SLgSessionFactoryImpl;
import org.jdiameter.server.impl.app.slg.SLgServerSessionImpl;
import org.mobicents.protocols.ss7.map.api.MAPException;
import org.mobicents.protocols.ss7.map.api.service.lsm.VelocityType;
import org.mobicents.protocols.ss7.map.api.service.mobility.subscriberInformation.TypeOfShape;
import org.mobicents.protocols.ss7.map.service.lsm.ExtGeographicalInformationImpl;
import org.mobicents.protocols.ss7.map.service.lsm.VelocityEstimateImpl;
import org.mobicents.servers.diameter.location.data.SubscriberElement;
import org.mobicents.servers.diameter.location.data.SubscriberInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * @author <a href="mailto:aferreiraguido@gmail.com"> Alejandro Ferreira Guido </a>
 * @author <a href="mailto:fernando.mendioroz@gmail.com"> Fernando Mendioroz </a>
 */
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

    private SubscriberInformation subscriberInformation;

    public SLgReferencePoint(SubscriberInformation subscriberInformation) throws Exception {
        super();

        this.subscriberInformation = subscriberInformation;
    }

    public Answer processRequest(Request request) {
        if (logger.isInfoEnabled()) {
            logger.info("<< Received SLg request [" + request + "]");
        }

        try {
            ApplicationId slgAppId = ApplicationId.createByAuthAppId(0, this.getApplicationId());
            SLgServerSessionImpl session = sessionFactory.getNewAppSession(request.getSessionId(), slgAppId, ServerSLgSession.class, EMPTY_ARRAY);
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

    @Override
    public void doProvideLocationRequestEvent(ServerSLgSession session, ProvideLocationRequest plr)
            throws InternalException, IllegalDiameterStateException, RouteException, OverloadException, AvpDataException {

        int resultCode = ResultCode.SUCCESS;

        String msisdn = "";
        String imsi = "";

        if (logger.isInfoEnabled()) {
            logger.info("<> Processing [PLR] Provide-Location-Request for request [" + plr + "] session-id [" + session.getSessionId() +"]");
        }

        AvpSet plrAvpSet = plr.getMessage().getAvps();

        plrAvpSet.getAvp(Avp.SLG_LOCATION_TYPE).getInteger32();
        if (plrAvpSet.getAvp(Avp.USER_NAME) != null) {
            imsi = plrAvpSet.getAvp(Avp.USER_NAME).getUTF8String();
        }

        if (plrAvpSet.getAvp(Avp.MSISDN) != null) {
            msisdn = plrAvpSet.getAvp(Avp.MSISDN).getUTF8String();
        }

        if (logger.isInfoEnabled()) {
            logger.info("<> Generating [PLA] Provide-Location-Answer response data");
        }

        SubscriberElement subscriberElement = null;
        try {
            subscriberElement = subscriberInformation.getElementBySubscriber(imsi, msisdn);
            resultCode = subscriberElement.locationResult;
        } catch (Exception e) {
            if (e.getMessage().equals("SubscriberNotFound"))
                resultCode = DIAMETER_ERROR_USER_UNKNOWN;
        }

        ProvideLocationAnswer pla = new ProvideLocationAnswerImpl((Request) plr.getMessage(), resultCode);
        AvpSet plaAvpSet = pla.getMessage().getAvps();

        if (resultCode == ResultCode.SUCCESS) {
            try {
                plaAvpSet.addAvp(Avp.LOCATION_ESTIMATE,
                        new ExtGeographicalInformationImpl(TypeOfShape.getInstance(subscriberElement.locationEstimate.typeOfShape),
                                subscriberElement.locationEstimate.latitude,
                                subscriberElement.locationEstimate.longitude,
                                subscriberElement.locationEstimate.uncertainty,
                                subscriberElement.locationEstimate.uncertaintySemiMajorAxis,
                                subscriberElement.locationEstimate.uncertaintySemiMinorAxis,
                                subscriberElement.locationEstimate.angleOfMajorAxis,
                                subscriberElement.locationEstimate.confidence,
                                subscriberElement.locationEstimate.altitude,
                                subscriberElement.locationEstimate.uncertaintyAltitude,
                                subscriberElement.locationEstimate.innerRadius,
                                subscriberElement.locationEstimate.uncertaintyInnerRadius,
                                subscriberElement.locationEstimate.offsetAngle,
                                subscriberElement.locationEstimate.includedAngle).getData(),10415, true, false);
                plaAvpSet.addAvp(Avp.ACCURACY_FULFILMENT_INDICATOR, subscriberElement.accuracyFulfilmentIndicator, 10415, true, false, true);
                plaAvpSet.addAvp(Avp.AGE_OF_LOCATION_ESTIMATE, subscriberElement.ageOfLocationEstimate, 10415, true, false, true);
                plaAvpSet.addAvp(Avp.VELOCITY_ESTIMATE,
                        new VelocityEstimateImpl(VelocityType.getInstance(subscriberElement.velocityEstimate.velocityType),
                                subscriberElement.velocityEstimate.horizontalSpeed,
                                subscriberElement.velocityEstimate.bearing,
                                subscriberElement.velocityEstimate.verticalSpeed,
                                subscriberElement.velocityEstimate.uncertaintyHorizontalSpeed,
                                subscriberElement.velocityEstimate.uncertaintyVerticalSpeed).getData(), 10415, true, false);
                plaAvpSet.addAvp(Avp.EUTRAN_POSITIONING_DATA, subscriberElement.eutranPositioningData, 10415, true, false, true);
                plaAvpSet.addAvp(Avp.ECGI, subscriberElement.eutranCellGlobalIdentity, 10415, true, false, true);

                AvpSet geranPositioningInfo = plaAvpSet.addGroupedAvp(Avp.GERAN_POSITIONING_INFO, 10415, false, false);
                geranPositioningInfo.addAvp(Avp.GERAN_POSITIONING_DATA, subscriberElement.geranPositioningData, 10415, false, false, true);
                geranPositioningInfo.addAvp(Avp.GERAN_GANSS_POSITIONING_DATA, subscriberElement.geranGanssPositioningData, 10415, false, false, true);

                plaAvpSet.addAvp(Avp.CELL_GLOBAL_IDENTITY, subscriberElement.cellGlobalIdentity, 10415, false, false, true);

                AvpSet utranPositioningInfo = plaAvpSet.addGroupedAvp(Avp.UTRAN_POSITIONING_INFO, 10415, false, false);
                utranPositioningInfo.addAvp(Avp.UTRAN_POSITIONING_DATA, subscriberElement.utranPositioningData, 10415, false, false, true);
                utranPositioningInfo.addAvp(Avp.UTRAN_GANSS_POSITIONING_DATA, subscriberElement.utranGanssPositioningData, 10415, false, false, true);
                utranPositioningInfo.addAvp(Avp.UTRAN_ADDITIONAL_POSITIONING_DATA, subscriberElement.utranAdditionalPositioningData,10415,false,false,true);

                plaAvpSet.addAvp(Avp.SERVICE_AREA_IDENTITY, subscriberElement.serviceAreaIdentity, 10415, false, false, true);

                AvpSet servingNode = plaAvpSet.addGroupedAvp(Avp.SERVING_NODE, 10415, true, false);
                servingNode.addAvp(Avp.SGSN_NUMBER, subscriberElement.servingNode.sgsnNumber, 10415, true, false, true);
                servingNode.addAvp(Avp.SGSN_NAME, subscriberElement.servingNode.sgsnName, 10415, false, false, false);
                servingNode.addAvp(Avp.SGSN_REALM, subscriberElement.servingNode.sgsnRealm, 10415, false, false, false);
                servingNode.addAvp(Avp.MME_NAME, subscriberElement.servingNode.mmeName, 10415, true, false, false);
                servingNode.addAvp(Avp.MME_REALM, subscriberElement.servingNode.mmeRealm, 10415, false, false, false);
                servingNode.addAvp(Avp.MSC_NUMBER, subscriberElement.servingNode.mscNumber, 10415, true, false, true);
                servingNode.addAvp(Avp.TGPP_AAA_SERVER_NAME, subscriberElement.servingNode.tgppAAAServerName, 10415, true, false, false);
                servingNode.addAvp(Avp.LCS_CAPABILITIES_SETS, subscriberElement.servingNode.lcsCapabilitySets, 10415, true, false, true);
                servingNode.addAvp(Avp.GMLC_ADDRESS, subscriberElement.servingNode.gmlcAddress, 10415, true, false, true);

                plaAvpSet.addAvp(Avp.PLA_FLAGS, subscriberElement.plaFlags, 10415, false, false, true);

                AvpSet esmlcCellInfo = plaAvpSet.addGroupedAvp(Avp.ESMLC_CELL_INFO, 10415, false, false);
                esmlcCellInfo.addAvp(Avp.ECGI, subscriberElement.esmlcCellInfoEcgi, 10415, false, false, true);
                esmlcCellInfo.addAvp(Avp.CELL_PORTION_ID, subscriberElement.esmlcCellInfoCpi, 10415, false, false, true);

                plaAvpSet.addAvp(Avp.CIVIC_ADDRESS, subscriberElement.civicAddress, 10415, false, false, false);
                plaAvpSet.addAvp(Avp.BAROMETRIC_PRESSURE, subscriberElement.barometricPressure, 10415, false, false, true);

            } catch (MAPException e) {
                logger.info(">< Error generating Provide-Location-Answer", e);
            }
        }

        if (logger.isInfoEnabled()) {
            if (resultCode == DIAMETER_ERROR_USER_UNKNOWN)
                logger.info("<> Sending [PLA] Provide-Location-Answer with result code: "+resultCode+", DIAMETER_ERROR_USER_UNKNOWN");
            else if (resultCode == DIAMETER_ERROR_UNAUTHORIZED_REQUESTING_NETWORK)
                logger.info("<> Sending [PLA] Provide-Location-Answer with result code: "+resultCode+", DIAMETER_ERROR_UNAUTHORIZED_REQUESTING_NETWORK");
            else if (resultCode == DIAMETER_ERROR_UNREACHABLE_USER)
                logger.info("<> Sending [PLA] Provide-Location-Answer with result code: "+resultCode+", DIAMETER_ERROR_UNREACHABLE_USER");
            else if (resultCode == DIAMETER_ERROR_SUSPENDED_USER)
                logger.info("<> Sending [PLA] Provide-Location-Answer with result code: "+resultCode+", DIAMETER_ERROR_SUSPENDED_USER");
            else if (resultCode == DIAMETER_ERROR_DETACHED_USER)
                logger.info("<> Sending [PLA] Provide-Location-Answer with result code: "+resultCode+", DIAMETER_ERROR_DETACHED_USER");
            else if (resultCode == DIAMETER_ERROR_POSITIONING_DENIED)
                logger.info("<> Sending [PLA] Provide-Location-Answer with result code: "+resultCode+", DIAMETER_ERROR_POSITIONING_DENIED");
            else if (resultCode == DIAMETER_ERROR_POSITIONING_FAILED)
                logger.info("<> Sending [PLA] Provide-Location-Answer with result code: "+resultCode+", DIAMETER_ERROR_POSITIONING_FAILED");
            else if (resultCode == DIAMETER_ERROR_UNKNOWN_UNREACHABLE)
                logger.info("<> Sending [PLA] Provide-Location-Answer with result code: "+resultCode+", DIAMETER_ERROR_UNKNOWN_UNREACHABLE");
            else if (resultCode == ResultCode.UNABLE_TO_DELIVER)
                logger.info("<> Sending [PLA] Provide-Location-Answer with result code: "+resultCode+", UNABLE_TO_DELIVER");
            else if (resultCode == ResultCode.SUCCESS)
                logger.info("<> Sending [PLA] Provide-Location-Answer with result code: "+resultCode+", SUCCESS");
            else
                logger.info("<> Sending [PLA] Provide-Location-Answer with result code: "+resultCode);

        }

        session.sendProvideLocationAnswer(pla);
    }

    public void sendLocationReportRequest(String msisdn, Integer locationEventType, String lcsReferenceNumber)
            throws InternalException, RouteException, OverloadException, IllegalDiameterStateException {

        int resultCode = ResultCode.SUCCESS;

        if (logger.isInfoEnabled()) {
            logger.info("<> Generating [LRR] Location-Report-Request data for sending to GMLC");
        }

        SubscriberElement subscriberElement = null;
        try {
            subscriberElement = subscriberInformation.getElementBySubscriber("", msisdn);

            String sessionId = UUID.randomUUID().toString();
            ServerSLgSession session = ((ISessionFactory) this.sessionFactory).getNewAppSession(sessionId,
                    ApplicationId.createByAuthAppId(10415, 16777255), ServerSLgSession.class, null);

            LocationReportRequest lrr = new LocationReportRequestImpl(session.getSessions().get(0).createRequest(LocationReportRequest.code,
                    ApplicationId.createByAuthAppId(10415, 16777255), "be-connect.us"));

            AvpSet lrrAvpSet = lrr.getMessage().getAvps();

            lrrAvpSet.addAvp(Avp.LOCATION_EVENT, locationEventType, 10415, true, false, true);

            lrrAvpSet.addAvp(Avp.USER_NAME, subscriberElement.imsi, true, false, false);
            lrrAvpSet.addAvp(Avp.MSISDN, subscriberElement.msisdn, 10415, true, false, true);
            lrrAvpSet.addAvp(Avp.TGPP_IMEI, subscriberElement.imei, 10415, true, false, false);

            AvpSet lcsEpsClientName = lrrAvpSet.addGroupedAvp(Avp.LCS_EPS_CLIENT_NAME, 10415, true, false);
            lcsEpsClientName.addAvp(Avp.LCS_NAME_STRING, subscriberElement.lcsEpsClientNameString, 10415, true, false, false);
            lcsEpsClientName.addAvp(Avp.LCS_FORMAT_INDICATOR, subscriberElement.lcsEpsClientNameFormatInd, 10415, true, false, false);

            lrrAvpSet.addAvp(Avp.LOCATION_ESTIMATE,
                    new ExtGeographicalInformationImpl(TypeOfShape.getInstance(subscriberElement.locationEstimate.typeOfShape),
                            subscriberElement.locationEstimate.latitude,
                            subscriberElement.locationEstimate.longitude,
                            subscriberElement.locationEstimate.uncertainty,
                            subscriberElement.locationEstimate.uncertaintySemiMajorAxis,
                            subscriberElement.locationEstimate.uncertaintySemiMinorAxis,
                            subscriberElement.locationEstimate.angleOfMajorAxis,
                            subscriberElement.locationEstimate.confidence,
                            subscriberElement.locationEstimate.altitude,
                            subscriberElement.locationEstimate.uncertaintyAltitude,
                            subscriberElement.locationEstimate.innerRadius,
                            subscriberElement.locationEstimate.uncertaintyInnerRadius,
                            subscriberElement.locationEstimate.offsetAngle,
                            subscriberElement.locationEstimate.includedAngle).getData(), 10415, true, false);
            lrrAvpSet.addAvp(Avp.ACCURACY_FULFILMENT_INDICATOR, subscriberElement.accuracyFulfilmentIndicator, 10415, false, false);
            lrrAvpSet.addAvp(Avp.AGE_OF_LOCATION_ESTIMATE, subscriberElement.ageOfLocationEstimate, 10415, false, false, true);
            lrrAvpSet.addAvp(Avp.VELOCITY_ESTIMATE,
                    new VelocityEstimateImpl(VelocityType.getInstance(subscriberElement.velocityEstimate.velocityType),
                            subscriberElement.velocityEstimate.horizontalSpeed,
                            subscriberElement.velocityEstimate.bearing,
                            subscriberElement.velocityEstimate.verticalSpeed,
                            subscriberElement.velocityEstimate.uncertaintyHorizontalSpeed,
                            subscriberElement.velocityEstimate.uncertaintyVerticalSpeed).getData(), 10415, false, false);
            lrrAvpSet.addAvp(Avp.EUTRAN_POSITIONING_DATA, subscriberElement.eutranPositioningData, 10415, false, false, true);
            lrrAvpSet.addAvp(Avp.ECGI, subscriberElement.eutranCellGlobalIdentity,10415, false, false, true);

            AvpSet geranPositioningInfo = lrrAvpSet.addGroupedAvp(Avp.GERAN_POSITIONING_INFO, 10415, false, false);
            geranPositioningInfo.addAvp(Avp.GERAN_POSITIONING_DATA, subscriberElement.geranPositioningData, 10415, false, false,true);
            geranPositioningInfo.addAvp(Avp.GERAN_GANSS_POSITIONING_DATA, subscriberElement.geranGanssPositioningData, 10415, false, false,true);

            lrrAvpSet.addAvp(Avp.CELL_GLOBAL_IDENTITY, subscriberElement.cellGlobalIdentity, 10415, false, false, true);

            AvpSet utranPositioningInfo = lrrAvpSet.addGroupedAvp(Avp.UTRAN_POSITIONING_INFO,10415, false, false);
            utranPositioningInfo.addAvp(Avp.UTRAN_POSITIONING_DATA, subscriberElement.utranPositioningData, 10415, false, false, true);
            utranPositioningInfo.addAvp(Avp.UTRAN_GANSS_POSITIONING_DATA, subscriberElement.utranGanssPositioningData, 10415, false, false, true);
            utranPositioningInfo.addAvp(Avp.UTRAN_ADDITIONAL_POSITIONING_DATA, subscriberElement.utranAdditionalPositioningData, 10415, false, false, true);

            lrrAvpSet.addAvp(Avp.SERVICE_AREA_IDENTITY, subscriberElement.serviceAreaIdentity, 10415, false, false, true);
            lrrAvpSet.addAvp(Avp.LCS_SERVICE_TYPE_ID, subscriberElement.lcsServiceTypeId, 10415, true, false, true);
            lrrAvpSet.addAvp(Avp.PSEUDONYM_INDICATOR, subscriberElement.pseudonymIndicator, 10415, false, false,false);

            lrrAvpSet.addAvp(Avp.LCS_QOS_CLASS, subscriberElement.lcsQosClass, 10415, false, false, false);

            AvpSet servingNode = lrrAvpSet.addGroupedAvp(Avp.SERVING_NODE, 10415, false, false);
            servingNode.addAvp(Avp.SGSN_NUMBER, subscriberElement.servingNode.sgsnNumber, 10415, false, false, true);
            servingNode.addAvp(Avp.SGSN_NAME, subscriberElement.servingNode.sgsnName, 10415, false, false, false);
            servingNode.addAvp(Avp.SGSN_REALM, subscriberElement.servingNode.sgsnRealm, 10415, false, false, false);
            servingNode.addAvp(Avp.MME_NAME, subscriberElement.servingNode.mmeName, 10415, false, false, false);
            servingNode.addAvp(Avp.MME_REALM, subscriberElement.servingNode.mmeRealm, 10415, false, false, false);
            servingNode.addAvp(Avp.MSC_NUMBER, subscriberElement.servingNode.mscNumber, 10415, false, false, true);
            servingNode.addAvp(Avp.TGPP_AAA_SERVER_NAME, subscriberElement.servingNode.tgppAAAServerName, 10415, false, false, false);
            servingNode.addAvp(Avp.LCS_CAPABILITIES_SETS, subscriberElement.servingNode.lcsCapabilitySets, 10415, false, false, true);
            servingNode.addAvp(Avp.GMLC_ADDRESS, subscriberElement.servingNode.gmlcAddress, 10415, false, false, false);

            lrrAvpSet.addAvp(Avp.LRR_FLAGS, subscriberElement.lrrFlags, 10415, false, false, true);
            lrrAvpSet.addAvp(Avp.LCS_REFERENCE_NUMBER, lcsReferenceNumber, 10415, false, false, true);

            AvpSet deferredMtLrData = lrrAvpSet.addGroupedAvp(Avp.DEFERRED_MT_LR_DATA, 10415, false, false);
            deferredMtLrData.addAvp(Avp.DEFERRED_LOCATION_TYPE, subscriberElement.deferredMtLrDataLocationType,10415, false, false, true);
            deferredMtLrData.addAvp(Avp.TERMINATION_CAUSE, subscriberElement.deferredMtLrDataTerminationCause,false, false, true);
            AvpSet deferredMtLrDataServingNode = deferredMtLrData.addGroupedAvp(Avp.SERVING_NODE, 10415, true, false);
            deferredMtLrDataServingNode.addAvp(Avp.SGSN_NUMBER, subscriberElement.deferredMtLrDataServingNode.sgsnNumber, 10415, false, false, true);
            deferredMtLrDataServingNode.addAvp(Avp.SGSN_NAME, subscriberElement.deferredMtLrDataServingNode.sgsnName, 10415, false, false, false);
            deferredMtLrDataServingNode.addAvp(Avp.SGSN_REALM, subscriberElement.deferredMtLrDataServingNode.sgsnRealm, 10415, false, false, false);
            deferredMtLrDataServingNode.addAvp(Avp.MME_NAME, subscriberElement.deferredMtLrDataServingNode.mmeName, 10415, false, false, false);
            deferredMtLrDataServingNode.addAvp(Avp.MME_REALM, subscriberElement.deferredMtLrDataServingNode.mmeRealm, 10415, false, false, false);
            deferredMtLrDataServingNode.addAvp(Avp.MSC_NUMBER, subscriberElement.deferredMtLrDataServingNode.mscNumber, 10415, false, false, true);
            deferredMtLrDataServingNode.addAvp(Avp.TGPP_AAA_SERVER_NAME, subscriberElement.deferredMtLrDataServingNode.tgppAAAServerName, 10415, false, false, false);
            deferredMtLrDataServingNode.addAvp(Avp.LCS_CAPABILITIES_SETS, subscriberElement.deferredMtLrDataServingNode.lcsCapabilitySets, 10415, false, false, true);
            deferredMtLrDataServingNode.addAvp(Avp.GMLC_ADDRESS, subscriberElement.deferredMtLrDataServingNode.gmlcAddress, 10415, false, false, false);

            lrrAvpSet.addAvp(Avp.GMLC_ADDRESS, subscriberElement.gmlcAddress, 10415, false, false, true);

            lrrAvpSet.addAvp(Avp.REPORTING_AMOUNT, subscriberElement.reportingAmount,10415, false, false, true);

            AvpSet periodicLdrInformation = lrrAvpSet.addGroupedAvp(Avp.PERIODIC_LDR_INFORMATION, 10415, false, false);
            periodicLdrInformation.addAvp(Avp.REPORTING_INTERVAL, subscriberElement.reportingInterval, 10415, false, false, true);

            AvpSet esmlcCellInfo = lrrAvpSet.addGroupedAvp(Avp.ESMLC_CELL_INFO, 10415, false, false);
            esmlcCellInfo.addAvp(Avp.ECGI, subscriberElement.esmlcCellInfoEcgi, 10415, false, false, true);
            esmlcCellInfo.addAvp(Avp.CELL_PORTION_ID, subscriberElement.esmlcCellInfoCpi, 10415, false, false, true);

            lrrAvpSet.addAvp(Avp.ONEXRTT_RCID, subscriberElement.oneXRttRcid, 10415, false, false, true);

            AvpSet delayedLocationReportedData = lrrAvpSet.addGroupedAvp(DIAMETER_AVP_DELAYED_LOCATION_REPORTING_DATA, 10415, false, false);
            delayedLocationReportedData.addAvp(Avp.TERMINATION_CAUSE, 8, true, false, true);
            AvpSet delayedLocationReportedDataservingNode = delayedLocationReportedData.addGroupedAvp(Avp.SERVING_NODE, 10415, true, false);
            delayedLocationReportedDataservingNode.addAvp(Avp.SGSN_NUMBER, subscriberElement.delayedLocationDataServingNode.sgsnNumber, 10415, false, false, true);
            delayedLocationReportedDataservingNode.addAvp(Avp.SGSN_NAME, subscriberElement.delayedLocationDataServingNode.sgsnName, 10415, false, false, false);
            delayedLocationReportedDataservingNode.addAvp(Avp.SGSN_REALM, subscriberElement.delayedLocationDataServingNode.sgsnRealm, 10415, false, false, false);
            delayedLocationReportedDataservingNode.addAvp(Avp.MME_NAME, subscriberElement.delayedLocationDataServingNode.mmeName, 10415, false, false, false);
            delayedLocationReportedDataservingNode.addAvp(Avp.MME_REALM, subscriberElement.delayedLocationDataServingNode.mmeRealm, 10415, false, false, false);
            delayedLocationReportedDataservingNode.addAvp(Avp.MSC_NUMBER, subscriberElement.delayedLocationDataServingNode.mscNumber, 10415, false, false, true);
            delayedLocationReportedDataservingNode.addAvp(Avp.TGPP_AAA_SERVER_NAME, subscriberElement.delayedLocationDataServingNode.tgppAAAServerName, 10415, false, false, false);
            delayedLocationReportedDataservingNode.addAvp(Avp.LCS_CAPABILITIES_SETS, subscriberElement.delayedLocationDataServingNode.lcsCapabilitySets, 10415, false, false, true);
            delayedLocationReportedDataservingNode.addAvp(Avp.GMLC_ADDRESS, subscriberElement.delayedLocationDataServingNode.gmlcAddress, 10415, false, false, false);

            lrrAvpSet.addAvp(Avp.CIVIC_ADDRESS, subscriberElement.civicAddress, 10415, false, false,true);
            lrrAvpSet.addAvp(Avp.BAROMETRIC_PRESSURE, subscriberElement.barometricPressure, 10415, false, false, true);

            if (logger.isInfoEnabled()) {
                logger.info("<> Sending [LRR] Location-Report-Request to GMLC, session-id [" + session.getSessionId() +"]");
            }

            session.sendLocationReportRequest(lrr);

        } catch (Exception e) {
            logger.error(">< Got exception while issuing [LRR] Location-Report-Request", e);
        }

    }


    @Override
    public void doLocationReportAnswerEvent(ServerSLgSession session, LocationReportRequest lrr, LocationReportAnswer lra)
            throws InternalException, IllegalDiameterStateException, RouteException, OverloadException, AvpDataException {

        if (logger.isInfoEnabled()) {
            logger.info("<> Got [LRA] Location-Report-Answer for request [" + lrr + "] and answer [" + lra + "]");
        }

        AvpSet lraAvpSet = lra.getMessage().getAvps();

        Integer resultCode = lraAvpSet.getAvp(Avp.RESULT_CODE).getInteger32();

        String gmlcAddress = lraAvpSet.getAvp(Avp.GMLC_ADDRESS).toString();
        Integer lraFlags = lraAvpSet.getAvp(Avp.LRA_FLAGS).getInteger32();
        Object reportingPlnmList = lraAvpSet.getAvp(Avp.REPORTING_PLMN_LIST);
        String lcsReferenceNumber = lraAvpSet.getAvp(Avp.LCS_REFERENCE_NUMBER).toString();

    }
}
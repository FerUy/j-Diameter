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
import org.jdiameter.api.app.AppAnswerEvent;
import org.jdiameter.api.slg.ServerSLgSession;
import org.jdiameter.api.slg.events.LocationReportAnswer;
import org.jdiameter.api.slg.events.LocationReportRequest;
import org.jdiameter.api.slg.events.ProvideLocationAnswer;
import org.jdiameter.api.slg.events.ProvideLocationRequest;
import org.jdiameter.client.api.ISessionFactory;
import org.jdiameter.common.impl.app.AppAnswerEventImpl;
import org.jdiameter.common.impl.app.slg.LocationReportRequestImpl;
import org.jdiameter.common.impl.app.slg.ProvideLocationAnswerImpl;
import org.jdiameter.common.impl.app.slg.SLgSessionFactoryImpl;
import org.jdiameter.server.impl.app.slg.SLgServerSessionImpl;
import org.mobicents.protocols.ss7.map.api.MAPException;
import org.mobicents.protocols.ss7.map.api.service.lsm.AddGeographicalInformation;
import org.mobicents.protocols.ss7.map.api.service.lsm.VelocityType;
import org.mobicents.protocols.ss7.map.api.service.mobility.subscriberInformation.TypeOfShape;
import org.mobicents.protocols.ss7.map.service.lsm.AddGeographicalInformationImpl;
import org.mobicents.protocols.ss7.map.service.lsm.ExtGeographicalInformationImpl;
import org.mobicents.protocols.ss7.map.service.lsm.VelocityEstimateImpl;
import org.mobicents.servers.diameter.location.data.SubscriberElement;
import org.mobicents.servers.diameter.location.data.SubscriberInformation;
import org.mobicents.servers.diameter.location.data.elements.EllipsoidPoint;
import org.mobicents.servers.diameter.location.data.elements.Polygon;
import org.mobicents.servers.diameter.location.data.elements.PolygonImpl;
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
        Integer lcsReferenceNumber = null;

        if (logger.isInfoEnabled()) {
            logger.info("<> Processing [PLR] Provide-Location-Request for request [" + plr + "] from "+ plr.getOriginHost() + "@" +plr.getOriginRealm() +
                " with session-id [" + session.getSessionId() +"]");
        }

        AvpSet plrAvpSet = plr.getMessage().getAvps();

        if (plr.getMessage().getAvps().getAvp(Avp.LCS_REFERENCE_NUMBER) != null)
            lcsReferenceNumber = plr.getMessage().getAvps().getAvp(Avp.LCS_REFERENCE_NUMBER).getInteger32();

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
            if (e.getMessage().equals("ApplicationUnsupported"))
                resultCode = ResultCode.APPLICATION_UNSUPPORTED;
        }

        ProvideLocationAnswer pla = new ProvideLocationAnswerImpl((Request) plr.getMessage(), resultCode);
        AvpSet plaAvpSet = pla.getMessage().getAvps();

        if (resultCode == ResultCode.SUCCESS) {
            try {
                if (subscriberElement.locationEstimate != null || subscriberElement.addLocationEstimate !=null) {
                    if (subscriberElement.locationEstimate != null &&
                        TypeOfShape.getInstance(subscriberElement.locationEstimate.typeOfShape) != TypeOfShape.Polygon) {
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
                    } else if (subscriberElement.addLocationEstimate !=null &&
                        TypeOfShape.getInstance(subscriberElement.addLocationEstimate.typeOfShape) == TypeOfShape.Polygon) {
                        EllipsoidPoint ellipsoidPoint1 = new EllipsoidPoint(subscriberElement.addLocationEstimate.latitude1,
                            subscriberElement.addLocationEstimate.longitude1);
                        EllipsoidPoint ellipsoidPoint2 = new EllipsoidPoint(subscriberElement.addLocationEstimate.latitude2,
                            subscriberElement.addLocationEstimate.longitude2);
                        EllipsoidPoint ellipsoidPoint3 = new EllipsoidPoint(subscriberElement.addLocationEstimate.latitude3,
                            subscriberElement.addLocationEstimate.longitude3);
                        EllipsoidPoint ellipsoidPoint4 = new EllipsoidPoint(subscriberElement.addLocationEstimate.latitude4,
                            subscriberElement.addLocationEstimate.longitude4);
                        EllipsoidPoint[] ellipsoidPoints = {ellipsoidPoint1, ellipsoidPoint2, ellipsoidPoint3, ellipsoidPoint4};
                        Polygon polygon = new PolygonImpl();
                        ((PolygonImpl) polygon).setData(ellipsoidPoints);
                        AddGeographicalInformation additionalLocationEstimate = new AddGeographicalInformationImpl(polygon.getData());
                        plaAvpSet.addAvp(Avp.LOCATION_ESTIMATE, additionalLocationEstimate.getData(),10415, true, false);
                    }
                }

                if (subscriberElement.accuracyFulfilmentIndicator != null)
                    plaAvpSet.addAvp(Avp.ACCURACY_FULFILMENT_INDICATOR, subscriberElement.accuracyFulfilmentIndicator, 10415, true, false, true);

                if (subscriberElement.ageOfLocationEstimate != null)
                    plaAvpSet.addAvp(Avp.AGE_OF_LOCATION_ESTIMATE, subscriberElement.ageOfLocationEstimate, 10415, true, false, true);

                if (subscriberElement.velocityEstimate != null) {
                    plaAvpSet.addAvp(Avp.VELOCITY_ESTIMATE,
                        new VelocityEstimateImpl(VelocityType.getInstance(subscriberElement.velocityEstimate.velocityType),
                            subscriberElement.velocityEstimate.horizontalSpeed,
                            subscriberElement.velocityEstimate.bearing,
                            subscriberElement.velocityEstimate.verticalSpeed,
                            subscriberElement.velocityEstimate.uncertaintyHorizontalSpeed,
                            subscriberElement.velocityEstimate.uncertaintyVerticalSpeed).getData(), 10415, true, false);
                }

                if (subscriberElement.eutranPositioningData != null) {
                    byte[] etranPositioningDataBytes = {35, 92, 106, 25, 17};
                    String etranPositioningDataString = new String(etranPositioningDataBytes);
                    plaAvpSet.addAvp(Avp.EUTRAN_POSITIONING_DATA, etranPositioningDataString, 10415, true, false, true);
                    //plaAvpSet.addAvp(Avp.EUTRAN_POSITIONING_DATA, subscriberElement.eutranPositioningData, 10415, true, false, true);
                }

                if (subscriberElement.eutranCellGlobalIdentity != null)
                    plaAvpSet.addAvp(Avp.ECGI, subscriberElement.eutranCellGlobalIdentity, 10415, true, false, true);

                if (subscriberElement.geranPositioningData != null && subscriberElement.geranGanssPositioningData != null) {
                    AvpSet geranPositioningInfo = plaAvpSet.addGroupedAvp(Avp.GERAN_POSITIONING_INFO, 10415, false, false);
                    geranPositioningInfo.addAvp(Avp.GERAN_POSITIONING_DATA, subscriberElement.geranPositioningData, 10415, false, false, true);
                    geranPositioningInfo.addAvp(Avp.GERAN_GANSS_POSITIONING_DATA, subscriberElement.geranGanssPositioningData, 10415, false, false, true);
                }

                if (subscriberElement.cellGlobalIdentity != null)
                    plaAvpSet.addAvp(Avp.CELL_GLOBAL_IDENTITY, subscriberElement.cellGlobalIdentity, 10415, false, false, true);

                if (subscriberElement.utranPositioningData != null && subscriberElement.utranGanssPositioningData != null &&
                    subscriberElement.utranAdditionalPositioningData != null) {
                    AvpSet utranPositioningInfo = plaAvpSet.addGroupedAvp(Avp.UTRAN_POSITIONING_INFO, 10415, false, false);
                    utranPositioningInfo.addAvp(Avp.UTRAN_POSITIONING_DATA, subscriberElement.utranPositioningData, 10415, false, false, true);
                    utranPositioningInfo.addAvp(Avp.UTRAN_GANSS_POSITIONING_DATA, subscriberElement.utranGanssPositioningData, 10415, false, false, true);
                    utranPositioningInfo.addAvp(Avp.UTRAN_ADDITIONAL_POSITIONING_DATA, subscriberElement.utranAdditionalPositioningData,10415,false,false,true);
                }

                if (subscriberElement.serviceAreaIdentity != null)
                    plaAvpSet.addAvp(Avp.SERVICE_AREA_IDENTITY, subscriberElement.serviceAreaIdentity, 10415, false, false, true);

                if (subscriberElement.servingNode != null) {
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
                }

                if (subscriberElement.plaFlags != null)
                    plaAvpSet.addAvp(Avp.PLA_FLAGS, subscriberElement.plaFlags, 10415, false, false, true);

                if (subscriberElement.esmlcCellInfoEcgi != null && subscriberElement.esmlcCellInfoCpi != null) {
                    AvpSet esmlcCellInfo = plaAvpSet.addGroupedAvp(Avp.ESMLC_CELL_INFO, 10415, false, false);
                    esmlcCellInfo.addAvp(Avp.ECGI, subscriberElement.esmlcCellInfoEcgi, 10415, false, false, true);
                    esmlcCellInfo.addAvp(Avp.CELL_PORTION_ID, subscriberElement.esmlcCellInfoCpi, 10415, false, false, true);
                }

                if (subscriberElement.civicAddress != null)
                    plaAvpSet.addAvp(Avp.CIVIC_ADDRESS, subscriberElement.civicAddress, 10415, false, false, false);

                if (subscriberElement.barometricPressure != null)
                    plaAvpSet.addAvp(Avp.BAROMETRIC_PRESSURE, subscriberElement.barometricPressure, 10415, false, false, true);

            } catch (MAPException e) {
                logger.info(">< Error while generating Provide-Location-Answer", e);
            }
        }

        if (resultCode == DIAMETER_ERROR_USER_UNKNOWN) {
            if (lcsReferenceNumber != null)
                logger.info("<> Sending [PLA] Provide-Location-Answer with LCS-Reference-Number:" + lcsReferenceNumber +
                    " to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " and result code:" + resultCode + " (DIAMETER_ERROR_USER_UNKNOWN)");
            else
                logger.info("<> Sending [PLA] Provide-Location-Answer to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " with result code:"
                    + resultCode + " (DIAMETER_ERROR_USER_UNKNOWN)");
        } else if (resultCode == DIAMETER_ERROR_UNAUTHORIZED_REQUESTING_NETWORK) {
            if (lcsReferenceNumber != null)
                logger.info("<> Sending [PLA] Provide-Location-Answer with LCS-Reference-Number:" + lcsReferenceNumber +
                    " to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " and result code:" + resultCode + " (DIAMETER_ERROR_UNAUTHORIZED_REQUESTING_NETWORK)");
            else
                logger.info("<> Sending [PLA] Provide-Location-Answer to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " with result code:"
                    + resultCode + " (DIAMETER_ERROR_UNAUTHORIZED_REQUESTING_NETWORK)");
        } else if (resultCode == DIAMETER_ERROR_UNREACHABLE_USER) {
            if (lcsReferenceNumber != null)
                logger.info("<> Sending [PLA] Provide-Location-Answer with LCS-Reference-Number:" + lcsReferenceNumber +
                    " to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " and result code:" + resultCode + " (DIAMETER_ERROR_UNREACHABLE_USER)");
            else
                logger.info("<> Sending [PLA] Provide-Location-Answer to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " with result code:"
                    + resultCode + " (DIAMETER_ERROR_UNREACHABLE_USER)");
        } else if (resultCode == DIAMETER_ERROR_SUSPENDED_USER) {
            if (lcsReferenceNumber != null)
                logger.info("<> Sending [PLA] Provide-Location-Answer with LCS-Reference-Number:" + lcsReferenceNumber +
                    " to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " and result code:" + resultCode + " (DIAMETER_ERROR_SUSPENDED_USER)");
            else
                logger.info("<> Sending [PLA] Provide-Location-Answer to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " with result code:"
                    + resultCode + " (DIAMETER_ERROR_SUSPENDED_USER)");
        } else if (resultCode == DIAMETER_ERROR_DETACHED_USER) {
            if (lcsReferenceNumber != null)
                logger.info("<> Sending [PLA] Provide-Location-Answer with LCS-Reference-Number:" + lcsReferenceNumber +
                    " to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " and result code:" + resultCode + " (DIAMETER_ERROR_DETACHED_USER)");
            else
                logger.info("<> Sending [PLA] Provide-Location-Answer to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " with result code:"
                    + resultCode + " (DIAMETER_ERROR_DETACHED_USER)");
        } else if (resultCode == DIAMETER_ERROR_POSITIONING_DENIED) {
            if (lcsReferenceNumber != null)
                logger.info("<> Sending [PLA] Provide-Location-Answer with LCS-Reference-Number:" + lcsReferenceNumber +
                    " to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " and result code:" + resultCode + " (DIAMETER_ERROR_POSITIONING_DENIED)");
            else
                logger.info("<> Sending [PLA] Provide-Location-Answer to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " with result code:"
                    + resultCode + " (DIAMETER_ERROR_POSITIONING_DENIED)");
        } else if (resultCode == DIAMETER_ERROR_POSITIONING_FAILED) {
            if (lcsReferenceNumber != null)
                logger.info("<> Sending [PLA] Provide-Location-Answer with LCS-Reference-Number:" + lcsReferenceNumber +
                    " to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " and result code:" + resultCode + " (DIAMETER_ERROR_POSITIONING_FAILED)");
            else
                logger.info("<> Sending [PLA] Provide-Location-Answer to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " with result code:"
                    + resultCode + " (DIAMETER_ERROR_POSITIONING_FAILED");
        } else if (resultCode == DIAMETER_ERROR_UNKNOWN_UNREACHABLE) {
            if (lcsReferenceNumber != null)
                logger.info("<> Sending [PLA] Provide-Location-Answer with LCS-Reference-Number:" + lcsReferenceNumber +
                    " to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " and result code:" + resultCode + " (DIAMETER_ERROR_UNKNOWN_UNREACHABLE)");
            else
                logger.info("<> Sending [PLA] Provide-Location-Answer to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " with result code:"
                    + resultCode + " (DIAMETER_ERROR_UNKNOWN_UNREACHABLE)");
        } else if (resultCode == ResultCode.SUCCESS) {
            if (lcsReferenceNumber != null)
                logger.info("<> Sending [PLA] Provide-Location-Answer with LCS-Reference-Number:" + lcsReferenceNumber +
                    " to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " and result code:" + resultCode + " (SUCCESS)");
            else
                logger.info("<> Sending [PLA] Provide-Location-Answer to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " with result code:"
                    + resultCode + " (SUCCESS)");
        } else {
            if (lcsReferenceNumber != null)
                logger.info("<> Sending [PLA] Provide-Location-Answer with LCS-Reference-Number:" + lcsReferenceNumber +
                    " to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " and result code:" + resultCode);
            else
                logger.info("<> Sending [PLA] Provide-Location-Answer to " + plr.getOriginHost() + "@" +plr.getOriginRealm() + " with result code:"
                    + resultCode);
        }
        session.sendProvideLocationAnswer(pla);
    }

    public void sendLocationReportRequest(String subscriberIdentity, Integer locationEventType, String lcsReferenceNumber, Boolean isImsi)
            throws InternalException, RouteException, OverloadException, IllegalDiameterStateException {

        int resultCode = ResultCode.SUCCESS;

        if (logger.isInfoEnabled()) {
            logger.info("<> Generating [LRR] Location-Report-Request data for sending to GMLC");
        }

        SubscriberElement subscriberElement;
        try {

            if (isImsi)
                subscriberElement = subscriberInformation.getElementBySubscriber(subscriberIdentity, "");
            else
                subscriberElement = subscriberInformation.getElementBySubscriber("", subscriberIdentity);

            String sessionId = UUID.randomUUID().toString();
            ServerSLgSession session = ((ISessionFactory) this.sessionFactory).getNewAppSession(sessionId,
                    ApplicationId.createByAuthAppId(10415, 16777255), ServerSLgSession.class, null);

            LocationReportRequest lrr = new LocationReportRequestImpl(session.getSessions().get(0).createRequest(LocationReportRequest.code,
                    ApplicationId.createByAuthAppId(10415, 16777255), "restcomm.org"));

            AvpSet lrrAvpSet = lrr.getMessage().getAvps();

            if (locationEventType != null)
                lrrAvpSet.addAvp(Avp.LOCATION_EVENT, locationEventType, 10415, true, false, true);

            if (subscriberElement.imsi != null)
                lrrAvpSet.addAvp(Avp.USER_NAME, subscriberElement.imsi, true, false, false);

            if (subscriberElement.msisdn != null)
                lrrAvpSet.addAvp(Avp.MSISDN, subscriberElement.msisdn, 10415, true, false, true);

            if (subscriberElement.imei != null)
                lrrAvpSet.addAvp(Avp.TGPP_IMEI, subscriberElement.imei, 10415, true, false, false);

            if (subscriberElement.lcsEpsClientNameString != null && subscriberElement.lcsEpsClientNameFormatInd != null) {
                AvpSet lcsEpsClientName = lrrAvpSet.addGroupedAvp(Avp.LCS_EPS_CLIENT_NAME, 10415, true, false);
                lcsEpsClientName.addAvp(Avp.LCS_NAME_STRING, subscriberElement.lcsEpsClientNameString, 10415, true, false, false);
                lcsEpsClientName.addAvp(Avp.LCS_FORMAT_INDICATOR, subscriberElement.lcsEpsClientNameFormatInd, 10415, true, false, true);
            }

            if (subscriberElement.locationEstimate != null  || subscriberElement.addLocationEstimate !=null) {
                if (subscriberElement.locationEstimate != null &&
                    TypeOfShape.getInstance(subscriberElement.locationEstimate.typeOfShape) != TypeOfShape.Polygon) {
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
                }  else if (subscriberElement.addLocationEstimate !=null &&
                    TypeOfShape.getInstance(subscriberElement.addLocationEstimate.typeOfShape) == TypeOfShape.Polygon) {
                        EllipsoidPoint ellipsoidPoint1 = new EllipsoidPoint(subscriberElement.addLocationEstimate.latitude1,
                            subscriberElement.addLocationEstimate.longitude1);
                        EllipsoidPoint ellipsoidPoint2 = new EllipsoidPoint(subscriberElement.addLocationEstimate.latitude2,
                            subscriberElement.addLocationEstimate.longitude2);
                        EllipsoidPoint ellipsoidPoint3 = new EllipsoidPoint(subscriberElement.addLocationEstimate.latitude3,
                            subscriberElement.addLocationEstimate.longitude3);
                        EllipsoidPoint ellipsoidPoint4 = new EllipsoidPoint(subscriberElement.addLocationEstimate.latitude4,
                            subscriberElement.addLocationEstimate.longitude4);
                        EllipsoidPoint[] ellipsoidPoints = {ellipsoidPoint1, ellipsoidPoint2, ellipsoidPoint3, ellipsoidPoint4};
                        Polygon polygon = new PolygonImpl();
                        ((PolygonImpl) polygon).setData(ellipsoidPoints);
                        AddGeographicalInformation additionalLocationEstimate = new AddGeographicalInformationImpl(polygon.getData());
                        lrrAvpSet.addAvp(Avp.LOCATION_ESTIMATE, additionalLocationEstimate.getData(),10415, true, false);
                }
            }

            if (subscriberElement.accuracyFulfilmentIndicator != null)
                lrrAvpSet.addAvp(Avp.ACCURACY_FULFILMENT_INDICATOR, subscriberElement.accuracyFulfilmentIndicator, 10415, false, false, true);

            if (subscriberElement.ageOfLocationEstimate != null)
                lrrAvpSet.addAvp(Avp.AGE_OF_LOCATION_ESTIMATE, subscriberElement.ageOfLocationEstimate, 10415, false, false, true);

            if (subscriberElement.velocityEstimate != null) {
                lrrAvpSet.addAvp(Avp.VELOCITY_ESTIMATE,
                    new VelocityEstimateImpl(VelocityType.getInstance(subscriberElement.velocityEstimate.velocityType),
                        subscriberElement.velocityEstimate.horizontalSpeed,
                        subscriberElement.velocityEstimate.bearing,
                        subscriberElement.velocityEstimate.verticalSpeed,
                        subscriberElement.velocityEstimate.uncertaintyHorizontalSpeed,
                        subscriberElement.velocityEstimate.uncertaintyVerticalSpeed).getData(), 10415, false, false);
            }

            if (subscriberElement.eutranPositioningData != null) {
                byte[] etranPositioningDataBytes = {35, 92, 16, 25, 43};
                String etranPositioningDataString = new String(etranPositioningDataBytes);
                lrrAvpSet.addAvp(Avp.EUTRAN_POSITIONING_DATA, etranPositioningDataString, 10415, true, false, true);
                //lrrAvpSet.addAvp(Avp.EUTRAN_POSITIONING_DATA, subscriberElement.eutranPositioningData, 10415, true, false, true);
            }

            if (subscriberElement.eutranCellGlobalIdentity != null)
                lrrAvpSet.addAvp(Avp.ECGI, subscriberElement.eutranCellGlobalIdentity,10415, false, false, true);

            if (subscriberElement.geranPositioningData != null && subscriberElement.geranGanssPositioningData != null) {
                AvpSet geranPositioningInfo = lrrAvpSet.addGroupedAvp(Avp.GERAN_POSITIONING_INFO, 10415, false, false);
                geranPositioningInfo.addAvp(Avp.GERAN_POSITIONING_DATA, subscriberElement.geranPositioningData, 10415, false, false,true);
                geranPositioningInfo.addAvp(Avp.GERAN_GANSS_POSITIONING_DATA, subscriberElement.geranGanssPositioningData, 10415, false, false,true);
            }

            if (subscriberElement.cellGlobalIdentity != null)
                lrrAvpSet.addAvp(Avp.CELL_GLOBAL_IDENTITY, subscriberElement.cellGlobalIdentity, 10415, false, false, true);

            if (subscriberElement.utranPositioningData != null && subscriberElement.utranGanssPositioningData != null &&
                subscriberElement.utranAdditionalPositioningData != null) {
                AvpSet utranPositioningInfo = lrrAvpSet.addGroupedAvp(Avp.UTRAN_POSITIONING_INFO,10415, false, false);
                utranPositioningInfo.addAvp(Avp.UTRAN_POSITIONING_DATA, subscriberElement.utranPositioningData, 10415, false, false, true);
                utranPositioningInfo.addAvp(Avp.UTRAN_GANSS_POSITIONING_DATA, subscriberElement.utranGanssPositioningData, 10415, false, false, true);
                utranPositioningInfo.addAvp(Avp.UTRAN_ADDITIONAL_POSITIONING_DATA, subscriberElement.utranAdditionalPositioningData, 10415, false, false, true);
            }

            if (subscriberElement.serviceAreaIdentity != null)
                lrrAvpSet.addAvp(Avp.SERVICE_AREA_IDENTITY, subscriberElement.serviceAreaIdentity, 10415, false, false, true);

            if (subscriberElement.lcsServiceTypeId != null)
                lrrAvpSet.addAvp(Avp.LCS_SERVICE_TYPE_ID, subscriberElement.lcsServiceTypeId, 10415, true, false, true);

            if (subscriberElement.pseudonymIndicator != null)
                lrrAvpSet.addAvp(Avp.PSEUDONYM_INDICATOR, subscriberElement.pseudonymIndicator, 10415, false, false,true);

            lrrAvpSet.addAvp(Avp.LCS_QOS_CLASS, subscriberElement.lcsQosClass, 10415, false, false, true);

            if (subscriberElement.servingNode != null) {
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
            }

            if (subscriberElement.lrrFlags != null)
                lrrAvpSet.addAvp(Avp.LRR_FLAGS, subscriberElement.lrrFlags, 10415, false, false, true);

            if (lcsReferenceNumber != null)
                lrrAvpSet.addAvp(Avp.LCS_REFERENCE_NUMBER, lcsReferenceNumber, 10415, false, false, true);

            if (subscriberElement.deferredMtLrDataServingNode != null) {
                AvpSet deferredMtLrData = lrrAvpSet.addGroupedAvp(Avp.DEFERRED_MT_LR_DATA, 10415, false, false);
                if (subscriberElement.deferredMtLrDataLocationType != null && subscriberElement.deferredMtLrDataTerminationCause != null) {
                    deferredMtLrData.addAvp(Avp.DEFERRED_LOCATION_TYPE, subscriberElement.deferredMtLrDataLocationType,10415, false, false, true);
                    deferredMtLrData.addAvp(Avp.TERMINATION_CAUSE_LCS, subscriberElement.deferredMtLrDataTerminationCause, 10415,false, false, true);
                }
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
            }

            if (subscriberElement.gmlcAddress != null)
                lrrAvpSet.addAvp(Avp.GMLC_ADDRESS, subscriberElement.gmlcAddress, 10415, false, false, true);

            if (subscriberElement.reportingInterval != null && subscriberElement.reportingAmount != null) {
                AvpSet periodicLdrInformation = lrrAvpSet.addGroupedAvp(Avp.PERIODIC_LDR_INFORMATION, 10415, false, false);
                periodicLdrInformation.addAvp(Avp.REPORTING_INTERVAL, subscriberElement.reportingInterval, 10415, false, false, true);
                periodicLdrInformation.addAvp(Avp.REPORTING_AMOUNT, subscriberElement.reportingAmount,10415, false, false, true);
            }

            if (subscriberElement.esmlcCellInfoEcgi != null && subscriberElement.esmlcCellInfoCpi != null) {
                AvpSet esmlcCellInfo = lrrAvpSet.addGroupedAvp(Avp.ESMLC_CELL_INFO, 10415, false, false);
                esmlcCellInfo.addAvp(Avp.ECGI, subscriberElement.esmlcCellInfoEcgi, 10415, false, false, true);
                esmlcCellInfo.addAvp(Avp.CELL_PORTION_ID, subscriberElement.esmlcCellInfoCpi, 10415, false, false, true);
            }

            if (subscriberElement.oneXRttRcid != null)
                lrrAvpSet.addAvp(Avp.ONEXRTT_RCID, subscriberElement.oneXRttRcid, 10415, false, false, true);

            if (subscriberElement.delayedLocationDataServingNode != null) {
                AvpSet delayedLocationReportedData = lrrAvpSet.addGroupedAvp(DIAMETER_AVP_DELAYED_LOCATION_REPORTING_DATA, 10415, false, false);
                delayedLocationReportedData.addAvp(Avp.TERMINATION_CAUSE_LCS, subscriberElement.delayedLocationDataTerminationCause, 10415, false, false, true);
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
            }

            if (subscriberElement.civicAddress != null)
                lrrAvpSet.addAvp(Avp.CIVIC_ADDRESS, subscriberElement.civicAddress, 10415, false, false,true);

            if (subscriberElement.barometricPressure != null)
                lrrAvpSet.addAvp(Avp.BAROMETRIC_PRESSURE, subscriberElement.barometricPressure, 10415, false, false, true);

            if (logger.isInfoEnabled()) {
                if (lcsReferenceNumber != null)
                    logger.info("<> Sending [LRR] Location-Report-Request to GMLC for session-id [" + session.getSessionId() +"] and LCS-Reference-Number:" + lcsReferenceNumber);
                else
                    logger.info("<> Sending [LRR] Location-Report-Request to GMLC for session-id [" + session.getSessionId() +"]");
            }

            session.sendLocationReportRequest(lrr);

        } catch (Exception e) {
            logger.error(">< Got exception while issuing [LRR] Location-Report-Request", e);
        }

    }


    @Override
    public void doLocationReportAnswerEvent(ServerSLgSession session, LocationReportRequest lrr, LocationReportAnswer lra)
            throws InternalException, IllegalDiameterStateException, RouteException, OverloadException, AvpDataException {

        Integer resultCode, lraFlags;
        String gmlcAddress, lcsReferenceNumber = null;
        Object reportingPlnmList;
        try {
            AvpSet lraAvpSet = lra.getMessage().getAvps();

            if (lraAvpSet != null) {

                if (lraAvpSet.getAvp(Avp.RESULT_CODE) != null)
                    resultCode = lraAvpSet.getAvp(Avp.RESULT_CODE).getInteger32();

                if (lraAvpSet.getAvp(Avp.GMLC_ADDRESS) != null)
                    gmlcAddress = lraAvpSet.getAvp(Avp.GMLC_ADDRESS).toString();

                if (lraAvpSet.getAvp(Avp.GMLC_ADDRESS) != null)
                    lraFlags = lraAvpSet.getAvp(Avp.LRA_FLAGS).getInteger32();

                if (lraAvpSet.getAvp(Avp.REPORTING_PLMN_LIST) != null)
                    reportingPlnmList = lraAvpSet.getAvp(Avp.REPORTING_PLMN_LIST);

                if (lraAvpSet.getAvp(Avp.LCS_REFERENCE_NUMBER) != null)
                    lcsReferenceNumber = lraAvpSet.getAvp(Avp.LCS_REFERENCE_NUMBER).toString();
            }

            if (logger.isInfoEnabled()) {
                logger.info("<> Got [LRA] Location-Report-Answer from " + lra.getOriginHost() + "@" + lra.getOriginRealm() + " for request [" + lrr + "] " +
                    "and session-id [" + session.getSessionId() + "]");
            }

        } catch (Exception e) {
            logger.error(">< Got exception while processing [LRA] Location-Report-Answer", e);
        }
    }
}

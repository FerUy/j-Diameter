package org.mobicents.servers.diameter.gmlc.points;

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
import org.jdiameter.api.RouteException;
import org.jdiameter.api.slh.ClientSLhSession;
import org.jdiameter.api.slh.ServerSLhSession;
import org.jdiameter.api.slh.events.LCSRoutingInfoAnswer;
import org.jdiameter.api.slh.events.LCSRoutingInfoRequest;
import org.jdiameter.client.api.ISessionFactory;
import org.jdiameter.common.impl.app.slh.LCSRoutingInfoRequestImpl;
import org.jdiameter.common.impl.app.slh.SLhSessionFactoryImpl;
import org.jdiameter.server.impl.app.slh.SLhServerSessionImpl;
import org.mobicents.servers.diameter.gmlc.GmlcDiameterService;
import org.mobicents.servers.diameter.gmlc.GmlcDiameterSession;
import org.mobicents.servers.diameter.gmlc.queue.IGmlcSubscriber;
import org.mobicents.servers.diameter.gmlc.queue.data.LocationInformation;

import org.mobicents.servers.diameter.gmlc.queue.data.LocationInformationResponse;
import org.mobicents.servers.diameter.gmlc.queue.data.elements.ServingNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class SLhReferencePoint extends SLhSessionFactoryImpl implements NetworkReqListener, EventListener<Request, Answer> {

    private static final Logger logger = LoggerFactory.getLogger(SLhReferencePoint.class);

    private static final int DIAMETER_ERROR_USER_UNKNOWN = 5001;
    private static final int DIAMETER_ERROR_UNAUTHORIZED_REQUESTING_NETWORK = 5490;
    private static final int DIAMETER_ERROR_ABSENT_USER = 4201;

    private static final Object[] EMPTY_ARRAY = new Object[]{};

    IGmlcSubscriber subscriber = null;

    public SLhReferencePoint(IGmlcSubscriber subscriber) throws Exception {
        super();
        this.subscriber = subscriber;
    }

    public Answer processRequest(Request request) {
        if (logger.isInfoEnabled()) {
            logger.info("<< Received SLh request [" + request + "]");
        }

        try {
            ApplicationId slhAppId = ApplicationId.createByAuthAppId(0, this.getApplicationId());
            SLhServerSessionImpl session = sessionFactory.getNewAppSession(request.getSessionId(), slhAppId, ServerSLhSession.class, EMPTY_ARRAY);
            session.processRequest(request);
        } catch (InternalException e) {
            logger.error(">< Failure handling SLh received request [" + request + "]", e);
        }

        return null;
    }

    public void receivedSuccessMessage(Request request, Answer answer) {
        if (logger.isInfoEnabled()) {
            logger.info("<< Received SLh message for request [" + request + "] and Answer [" + answer + "]");
        }
    }

    public void timeoutExpired(Request request) {
        if (logger.isInfoEnabled()) {
            logger.info("<< Received SLh timeout for request [" + request + "]");
        }
    }

    /**
     * Routing Information
     */
    public void performRoutingInfoRequest(LocationInformation locationInformation)
            throws InternalException, RouteException, OverloadException, IllegalDiameterStateException {

        if (logger.isInfoEnabled()) {
            logger.info("<> Generating [RIR] Routing-Information-Request with sessionId '" + locationInformation.getSessionId() + "'");
        }

        ClientSLhSession session = ((ISessionFactory) this.sessionFactory).getNewAppSession(locationInformation.getSessionId(),
                ApplicationId.createByAuthAppId(10415, this.getApplicationId()), ClientSLhSession.class, null);

        LCSRoutingInfoRequest rir = new LCSRoutingInfoRequestImpl(session.getSessions().get(0).createRequest(LCSRoutingInfoRequest.code,
                ApplicationId.createByAuthAppId(10415, this.getApplicationId()), "gmlc.beconnect.us"));

        AvpSet rirAvpSet = rir.getMessage().getAvps();

        if (locationInformation.getRequestParameters() != null) {
            if (locationInformation.getRequestParameters().imsi.length() > 0)
                rirAvpSet.addAvp(Avp.USER_NAME, locationInformation.getRequestParameters().imsi, true);

            if (locationInformation.getRequestParameters().msisdn.length() > 0)
                rirAvpSet.addAvp(Avp.MSISDN, locationInformation.getRequestParameters().msisdn, 10415, true, false,true);

            rirAvpSet.addAvp(Avp.GMLC_ADDRESS, "5989000075", 10415, true, false, true);

            session.sendLCSRoutingInfoRequest(rir);
        } else {
            locationInformation.setRoutingInformationError();
            subscriber.notificationReceived(locationInformation);
        }
    }

    @Override
    public void doLCSRoutingInfoAnswerEvent(ClientSLhSession session, LCSRoutingInfoRequest rir, LCSRoutingInfoAnswer ria)
            throws InternalException, IllegalDiameterStateException, RouteException, OverloadException {

        LocationInformation locationInformation = GmlcDiameterSession.getSavedSession(session.getSessionId());

        if (logger.isInfoEnabled()) {
            logger.info("<> Processing [RIA] Routing-Information-Answer for request session '" + session.getSessionId() +
                    "' for location information session '" + locationInformation.getSessionId() + "'");
        }

        AvpSet riaAvpSet = ria.getMessage().getAvps();

        LocationInformationResponse locationInformationResponse = locationInformation.createResponse(LocationInformation.LocationPrimitive.ProvideLocationRequest);

        try {
            locationInformationResponse.resultCode = riaAvpSet.getAvp(Avp.RESULT_CODE).getUnsigned32();

            if (riaAvpSet.getAvp(Avp.USER_NAME) != null)
                locationInformationResponse.imsi = riaAvpSet.getAvp(Avp.USER_NAME).getUTF8String();
            if (riaAvpSet.getAvp(Avp.MSISDN) != null)
                locationInformationResponse.msisdn = riaAvpSet.getAvp(Avp.MSISDN).getUTF8String();
            if (riaAvpSet.getAvp(Avp.LMSI) != null)
                locationInformationResponse.lmsi = riaAvpSet.getAvp(Avp.LMSI).getUTF8String();

            if (riaAvpSet.getAvp(Avp.SERVING_NODE) != null) {
                AvpSet servingNodeAvpSet = riaAvpSet.getAvp(Avp.SERVING_NODE).getGrouped();
                locationInformationResponse.servingNode = new ServingNode();

                if (servingNodeAvpSet.getAvp(Avp.SGSN_NUMBER) != null)
                    locationInformationResponse.servingNode.sgsnNumber = servingNodeAvpSet.getAvp(Avp.SGSN_NUMBER).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.SGSN_NAME) != null)
                    locationInformationResponse.servingNode.sgsnName = servingNodeAvpSet.getAvp(Avp.SGSN_NAME).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.SGSN_REALM) != null)
                    locationInformationResponse.servingNode.sgsnRealm = servingNodeAvpSet.getAvp(Avp.SGSN_REALM).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.MME_NAME) != null)
                    locationInformationResponse.servingNode.mmeName = servingNodeAvpSet.getAvp(Avp.MME_NAME).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.MME_REALM) != null)
                    locationInformationResponse.servingNode.mmeRealm = servingNodeAvpSet.getAvp(Avp.MME_REALM).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.MSC_NUMBER) != null)
                    locationInformationResponse.servingNode.mscNumber = servingNodeAvpSet.getAvp(Avp.MSC_NUMBER).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.TGPP_AAA_SERVER_NAME) != null)
                    locationInformationResponse.servingNode.tgppAAAServerName = servingNodeAvpSet.getAvp(Avp.TGPP_AAA_SERVER_NAME).getUTF8String();
                if (servingNodeAvpSet.getAvp(Avp.LCS_CAPABILITIES_SETS) != null)
                    locationInformationResponse.servingNode.lcsCapabilitySets = servingNodeAvpSet.getAvp(Avp.LCS_CAPABILITIES_SETS).getInteger32();
                if (servingNodeAvpSet.getAvp(Avp.GMLC_ADDRESS) != null)
                    locationInformationResponse.servingNode.gmlcAddress = servingNodeAvpSet.getAvp(Avp.GMLC_ADDRESS).getUTF8String();
            }

            if (riaAvpSet.getAvp(Avp.ADDITIONAL_SERVING_NODE) != null) {
                AvpSet additionalServingNodeAvpSet = riaAvpSet.getAvp(Avp.ADDITIONAL_SERVING_NODE).getGrouped();
                locationInformationResponse.additionalServingNode = new ServingNode();

                if (additionalServingNodeAvpSet.getAvp(Avp.SGSN_NUMBER) != null)
                    locationInformationResponse.additionalServingNode.sgsnNumber = additionalServingNodeAvpSet.getAvp(Avp.SGSN_NUMBER).getUTF8String();
                if (additionalServingNodeAvpSet.getAvp(Avp.SGSN_NAME) != null)
                    locationInformationResponse.additionalServingNode.sgsnName = additionalServingNodeAvpSet.getAvp(Avp.SGSN_NAME).getUTF8String();
                if (additionalServingNodeAvpSet.getAvp(Avp.SGSN_REALM) != null)
                    locationInformationResponse.additionalServingNode.sgsnRealm = additionalServingNodeAvpSet.getAvp(Avp.SGSN_REALM).getUTF8String();
                if (additionalServingNodeAvpSet.getAvp(Avp.MME_NAME) != null)
                    locationInformationResponse.additionalServingNode.mmeName = additionalServingNodeAvpSet.getAvp(Avp.MME_NAME).getUTF8String();
                if (additionalServingNodeAvpSet.getAvp(Avp.MME_REALM) != null)
                    locationInformationResponse.additionalServingNode.mmeRealm = additionalServingNodeAvpSet.getAvp(Avp.MME_REALM).getUTF8String();
                if (additionalServingNodeAvpSet.getAvp(Avp.MSC_NUMBER) != null)
                    locationInformationResponse.additionalServingNode.mscNumber = additionalServingNodeAvpSet.getAvp(Avp.MSC_NUMBER).getUTF8String();
                if (additionalServingNodeAvpSet.getAvp(Avp.TGPP_AAA_SERVER_NAME) != null)
                    locationInformationResponse.additionalServingNode.tgppAAAServerName = additionalServingNodeAvpSet.getAvp(Avp.TGPP_AAA_SERVER_NAME).getUTF8String();
                if (additionalServingNodeAvpSet.getAvp(Avp.LCS_CAPABILITIES_SETS) != null)
                    locationInformationResponse.additionalServingNode.lcsCapabilitySets = additionalServingNodeAvpSet.getAvp(Avp.LCS_CAPABILITIES_SETS).getInteger32();
                if (additionalServingNodeAvpSet.getAvp(Avp.GMLC_ADDRESS) != null)
                    locationInformationResponse.additionalServingNode.gmlcAddress = additionalServingNodeAvpSet.getAvp(Avp.GMLC_ADDRESS).getUTF8String();
            }

            if (riaAvpSet.getAvp(Avp.GMLC_ADDRESS) != null)
                locationInformationResponse.gmlcAddress = riaAvpSet.getAvp(Avp.GMLC_ADDRESS).getUTF8String();
            if (riaAvpSet.getAvp(Avp.PPR_ADDRESS) != null)
                locationInformationResponse.pprAddress = riaAvpSet.getAvp(Avp.PPR_ADDRESS).getUTF8String();
            if (riaAvpSet.getAvp(Avp.RIA_FLAGS) != null)
                locationInformationResponse.riaFlags = riaAvpSet.getAvp(Avp.RIA_FLAGS).getUnsigned32();
        } catch (AvpDataException e) {
            locationInformation.createResponse(LocationInformation.LocationPrimitive.RoutingInformationError);
            logger.error("*** Error processiong [RIA] Routing-Information-Answer parameters!", e);
        }

        subscriber.notificationReceived(locationInformation);
    }

}
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
import org.jdiameter.api.slh.ServerSLhSession;
import org.jdiameter.api.slh.events.LCSRoutingInfoAnswer;
import org.jdiameter.api.slh.events.LCSRoutingInfoRequest;
import org.jdiameter.common.impl.app.AppAnswerEventImpl;
import org.jdiameter.common.impl.app.slh.LCSRoutingInfoAnswerImpl;
import org.jdiameter.common.impl.app.slh.SLhSessionFactoryImpl;
import org.jdiameter.server.impl.app.slh.SLhServerSessionImpl;
import org.mobicents.servers.diameter.location.data.SubscriberElement;
import org.mobicents.servers.diameter.location.data.SubscriberInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author <a href="mailto:aferreiraguido@gmail.com"> Alejandro Ferreira Guido </a>
 * @author <a href="mailto:fernando.mendioroz@gmail.com"> Fernando Mendioroz </a>
 */
public class SLhReferencePoint extends SLhSessionFactoryImpl implements NetworkReqListener, EventListener<Request, Answer> {

    private static final Logger logger = LoggerFactory.getLogger(SLhReferencePoint.class);

    private static final int DIAMETER_ERROR_USER_UNKNOWN = 5001;
    private static final int DIAMETER_ERROR_UNAUTHORIZED_REQUESTING_NETWORK = 5490;
    private static final int DIAMETER_ERROR_ABSENT_USER = 4201;

    private static final Object[] EMPTY_ARRAY = new Object[]{};

    private SubscriberInformation subscriberInformation;

    public SLhReferencePoint(SubscriberInformation subscriberInformation) throws Exception {
        super();

        this.subscriberInformation = subscriberInformation;
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

    @Override
    public void doLCSRoutingInfoRequestEvent(ServerSLhSession session, LCSRoutingInfoRequest rir)
            throws InternalException, IllegalDiameterStateException, RouteException, OverloadException, AvpDataException {

        int resultCode = ResultCode.SUCCESS;

        Integer gmlcNumber = 0;
        String msisdn = "";
        String imsi = "";

        if (logger.isInfoEnabled()) {
            logger.info("<> Processing [RIR] Routing-Info-Request for request [" + rir + "] from " +rir.getOriginHost() + "@" +rir.getOriginRealm() +
                " with session-id [" + session.getSessionId() +"]");
        }

        AvpSet rirAvpSet = rir.getMessage().getAvps();

        if (rirAvpSet.getAvp(Avp.USER_NAME) != null) {
            imsi = rirAvpSet.getAvp(Avp.USER_NAME).getUTF8String();
        }

        if (rirAvpSet.getAvp(Avp.MSISDN) != null) {
            msisdn = rirAvpSet.getAvp(Avp.MSISDN).getUTF8String();
        }

        if (rirAvpSet.getAvp(Avp.GMLC_NUMBER) != null) {
            gmlcNumber = rirAvpSet.getAvp(Avp.GMLC_NUMBER).getInteger32();
        }

        if (logger.isInfoEnabled()) {
            logger.info("<> Generating [RIA] Routing-Info-Answer response data");
        }

        SubscriberElement subscriberElement = null;
        try {
            subscriberElement = subscriberInformation.getElementBySubscriber(imsi, msisdn);
            if (subscriberElement.locationResult == 5490)
                resultCode = DIAMETER_ERROR_UNAUTHORIZED_REQUESTING_NETWORK;
            if (subscriberElement.locationResult == 4201)
                resultCode = DIAMETER_ERROR_ABSENT_USER;
        } catch (Exception e) {
            if (e.getMessage().equals("SubscriberIncoherentData"))
                resultCode = ResultCode.CONTRADICTING_AVPS;
            else if (e.getMessage().equals("SubscriberNotFound"))
                resultCode = DIAMETER_ERROR_USER_UNKNOWN;
            if (e.getMessage().equals("ApplicationUnsupported"))
                resultCode = ResultCode.APPLICATION_UNSUPPORTED;
        }

        LCSRoutingInfoAnswer ria = new LCSRoutingInfoAnswerImpl((Request) rir.getMessage(), resultCode);

        if (resultCode == ResultCode.SUCCESS) {
            AvpSet riaAvpSet = ria.getMessage().getAvps();

            riaAvpSet.addAvp(Avp.USER_NAME, subscriberElement.imsi, 10415, true, false, false);
            riaAvpSet.addAvp(Avp.MSISDN, subscriberElement.msisdn, 10415, true, false, true);
            // Local Mobile Station Identity (LMSI) allocated by the VLR (MCC[3] + MNC[2|3] + MSIN max 15 digits)
            riaAvpSet.addAvp(Avp.LMSI, subscriberElement.lmsi, 10415, true, false, true);

            AvpSet servingNode = riaAvpSet.addGroupedAvp(Avp.SERVING_NODE, 10415, false, false);
            // contain the ISDN number of the serving MSC or MSC server in international number format
            servingNode.addAvp(Avp.SGSN_NUMBER, subscriberElement.servingNode.sgsnNumber, 10415, true, false, true);
            servingNode.addAvp(Avp.SGSN_NAME, subscriberElement.servingNode.sgsnName, 10415, false, false,false);
            servingNode.addAvp(Avp.SGSN_REALM, subscriberElement.servingNode.sgsnRealm, 10415, false, false,false);
            servingNode.addAvp(Avp.MME_NAME, subscriberElement.servingNode.mmeName, 10415, true, false,false);
            servingNode.addAvp(Avp.MME_REALM, subscriberElement.servingNode.mmeRealm, 10415, false, false,false);
            servingNode.addAvp(Avp.MSC_NUMBER, subscriberElement.servingNode.mscNumber, 10415, true, false,true);
            servingNode.addAvp(Avp.TGPP_AAA_SERVER_NAME, subscriberElement.servingNode.tgppAAAServerName, 10415, true, false,false);
            servingNode.addAvp(Avp.LCS_CAPABILITIES_SETS, subscriberElement.servingNode.lcsCapabilitySets, 10415, true, false, true);
            // IPv4 or IPv6 address of H-GMLC or the V-GMLC associated with the serving node
            InetAddress servingNodeGmlcAddress = null;
            try {
                servingNodeGmlcAddress = InetAddress.getByName(subscriberElement.servingNode.gmlcAddress);
                servingNodeGmlcAddress.isAnyLocalAddress();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            servingNode.addAvp(Avp.GMLC_ADDRESS, servingNodeGmlcAddress, 10415, true, false);

            if (subscriberElement.additionalServingNode != null) {
                AvpSet additionalServingNode = riaAvpSet.addGroupedAvp(Avp.ADDITIONAL_SERVING_NODE, 10415, false, false);
                additionalServingNode.addAvp(Avp.SGSN_NUMBER, subscriberElement.additionalServingNode.sgsnNumber, 10415, true, false, true);
                additionalServingNode.addAvp(Avp.SGSN_NAME, subscriberElement.additionalServingNode.sgsnName, 10415, false, false,false);
                additionalServingNode.addAvp(Avp.SGSN_REALM, subscriberElement.additionalServingNode.sgsnRealm, 10415, false, false,false);
                additionalServingNode.addAvp(Avp.MME_NAME, subscriberElement.additionalServingNode.mmeName, 10415, true, false,false);
                additionalServingNode.addAvp(Avp.MME_REALM, subscriberElement.additionalServingNode.mmeRealm, 10415, false, false,false);
                additionalServingNode.addAvp(Avp.MSC_NUMBER, subscriberElement.additionalServingNode.mscNumber, 10415, true, false, true);
                additionalServingNode.addAvp(Avp.TGPP_AAA_SERVER_NAME, subscriberElement.additionalServingNode.tgppAAAServerName, 10415, true, false, false);
                additionalServingNode.addAvp(Avp.LCS_CAPABILITIES_SETS, subscriberElement.additionalServingNode.lcsCapabilitySets, 10415, true, false, true);
                InetAddress addServingNodeGmlcAddress = null;
                try {
                    addServingNodeGmlcAddress = InetAddress.getByName(subscriberElement.servingNode.gmlcAddress);
                    addServingNodeGmlcAddress.isAnyLocalAddress();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                additionalServingNode.addAvp(Avp.GMLC_ADDRESS, addServingNodeGmlcAddress, 10415, false, true);
            }

            riaAvpSet.addAvp(Avp.GMLC_ADDRESS, subscriberElement.gmlcAddress, 10415, true, false, true);
            riaAvpSet.addAvp(Avp.PPR_ADDRESS, subscriberElement.pprAddress, 10415, true, false, true);
            riaAvpSet.addAvp(Avp.RIA_FLAGS, subscriberElement.riaFlags, 10415, true, false, true);
        }

        if (resultCode == DIAMETER_ERROR_USER_UNKNOWN) {
            logger.info("<> Sending [RIA] Routing-Info-Answer to " +rir.getOriginHost() + "@" +rir.getOriginRealm() + " with result code:" + resultCode +
                " (DIAMETER_ERROR_USER_UNKNOWN)");
        }
        else if (resultCode == DIAMETER_ERROR_UNAUTHORIZED_REQUESTING_NETWORK) {
            logger.info("<> Sending [RIA] Routing-Info-Answer to " + rir.getOriginHost() + "@" + rir.getOriginRealm() + " with result code:" + resultCode +
                " (DIAMETER_ERROR_UNAUTHORIZED_REQUESTING_NETWORK)");
        }
        else if (resultCode == DIAMETER_ERROR_ABSENT_USER) {
            logger.info("<> Sending [RIA] Routing-Info-Answer to " + rir.getOriginHost() + "@" + rir.getOriginRealm() + " with result code:" + resultCode +
                " (DIAMETER_ERROR_ABSENT_USER)");
        }
        else if (resultCode == ResultCode.SUCCESS) {
            logger.info("<> Sending [RIA] Routing-Info-Answer to " + rir.getOriginHost() + "@" + rir.getOriginRealm() + " with result code:" + resultCode +
                " (SUCCESS)");
        } else {
            logger.info("<> Sending Error-Answer to " + rir.getOriginHost() + "@" + rir.getOriginRealm() + " with result code:" + resultCode);
        }
        session.sendLCSRoutingInfoAnswer(ria);
    }

}
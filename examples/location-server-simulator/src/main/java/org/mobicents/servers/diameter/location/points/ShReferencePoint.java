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
import org.jdiameter.api.SessionFactory;
import org.jdiameter.api.app.AppAnswerEvent;
import org.jdiameter.api.app.AppRequestEvent;
import org.jdiameter.api.app.AppSession;
import org.jdiameter.api.sh.ServerShSession;
import org.jdiameter.api.sh.events.UserDataRequest;
import org.jdiameter.api.sh.events.UserDataAnswer;
import org.jdiameter.common.impl.app.AppAnswerEventImpl;
import org.jdiameter.common.impl.app.sh.ShSessionFactoryImpl;
import org.jdiameter.common.impl.app.sh.UserDataAnswerImpl;
import org.jdiameter.server.impl.app.sh.ShServerSessionImpl;
import org.mobicents.servers.diameter.location.data.SubscriberInformation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mobicents.servers.diameter.utils.TBCDUtil.parseTBCD;
import static org.mobicents.servers.diameter.utils.TBCDUtil.toTBCDString;

/**
 * @author <a href="mailto:aferreiraguido@gmail.com"> Alejandro Ferreira Guido </a>
 * @author <a href="mailto:fernando.mendioroz@gmail.com"> Fernando Mendioroz </a>
 */
public class ShReferencePoint extends ShSessionFactoryImpl implements NetworkReqListener, EventListener<Request, Answer> {

    private static final Logger logger = LoggerFactory.getLogger(ShReferencePoint.class);

    private static final int DIAMETER_ERROR_USER_DATA_NOT_RECOGNIZED = 5100;
    private static final int DIAMETER_ERROR_OPERATION_NOT_ALLOWED = 5101;
    private static final int DIAMETER_ERROR_USER_DATA_CANNOT_BE_READ = 5102;
    private static final int DIAMETER_ERROR_USER_DATA_CANNOT_BE_MODIFIED = 5103;
    private static final int DIAMETER_ERROR_USER_DATA_CANNOT_BE_NOTIFIED_ON_CHANGES = 5104;
    private static final int DIAMETER_ERROR_TRANSPARENT_DATA_OUT_OF_SYNC = 5105;
    private static final int DIAMETER_ERROR_SUBS_DATA_ABSENT = 5106;
    private static final int DIAMETER_ERROR_NO_SUBSCRIPTION_TO_DATA = 5107;
    private static final int DIAMETER_ERROR_DSAI_NOT_AVAILABLE = 5108;
    private static final int DIAMETER_ERROR_IDENTITIES_DONT_MATCH = 5002;
    private static final int DIAMETER_ERROR_TOO_MUCH_DATA = 5008;
    private static final int DIAMETER_USER_DATA_NOT_AVAILABLE = 4100;
    private static final int DIAMETER_PRIOR_UPDATE_IN_PROGRESS = 4101;

    private static final Object[] EMPTY_ARRAY = new Object[]{};

    private SubscriberInformation subscriberInformation;

    public ShReferencePoint(SessionFactory sessionFactory, SubscriberInformation subscriberInformation) throws Exception {
        super(sessionFactory);

        this.subscriberInformation = subscriberInformation;
    }

    public Answer processRequest(Request request) {
        if (logger.isInfoEnabled()) {
            logger.info("<< Received Sh request [" + request + "]");
        }

        try {
            ApplicationId shAppId = ApplicationId.createByAuthAppId(0, this.getApplicationId());
            ShServerSessionImpl session = sessionFactory.getNewAppSession(request.getSessionId(), shAppId, ServerShSession.class, EMPTY_ARRAY);
            session.processRequest(request);
        } catch (InternalException e) {
            logger.error(">< Failure handling Sh received request [" + request + "]", e);
        }

        return null;
    }

    public void receivedSuccessMessage(Request request, Answer answer) {
        if (logger.isInfoEnabled()) {
            logger.info("<< Received Sh message for request [" + request + "] and Answer [" + answer + "]");
        }
    }

    public void timeoutExpired(Request request) {
        if (logger.isInfoEnabled()) {
            logger.info("<< Received Sh timeout for request [" + request + "]");
        }
    }

    @Override
    public void doUserDataRequestEvent(ServerShSession session, UserDataRequest udr)
            throws InternalException, IllegalDiameterStateException, RouteException, OverloadException {

        int resultCode = ResultCode.SUCCESS;

        String msisdn = "";
        String publicIdentity = null;

        if (logger.isInfoEnabled()) {
            try {
                logger.info("<> Processing [UDR] User-Data-Request for request [" + udr + "] from " + udr.getOriginHost() + "@" + udr.getOriginRealm() +
                    " and session-id [" + session.getSessionId() +"]");
            } catch (AvpDataException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        AvpSet udrAvpSet = udr.getMessage().getAvps();

        if (udrAvpSet.getAvp(Avp.USER_IDENTITY) != null) {
            try {
                byte[] msisdnByteArray = null;
                if (udrAvpSet.getAvp(Avp.USER_IDENTITY).getGrouped().getAvp(Avp.MSISDN) != null)
                    msisdnByteArray = udrAvpSet.getAvp(Avp.USER_IDENTITY).getGrouped().getAvp(Avp.MSISDN).getOctetString();
                msisdn = toTBCDString(msisdnByteArray);
                if (udrAvpSet.getAvp(Avp.USER_IDENTITY).getGrouped().getAvp(Avp.PUBLIC_IDENTITY) != null)
                    publicIdentity = udrAvpSet.getAvp(Avp.USER_IDENTITY).getGrouped().getAvp(Avp.PUBLIC_IDENTITY).getUTF8String();
            } catch (AvpDataException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (logger.isInfoEnabled()) {
            if (publicIdentity != null)
                logger.info("<> Generating [UDA] User-Data-Answer response data for MSISDN=" + msisdn + ", and/or IMSPublicIdentity=" + publicIdentity);
            else
                logger.info("<> Generating [UDA] User-Data-Answer response data for MSISDN=" + msisdn);
        }

        String userData = null;
        try {
            userData = subscriberInformation.getUserDataBySubscriber(msisdn);
            if (userData != null)
                resultCode = ResultCode.SUCCESS;
            else
                resultCode = DIAMETER_ERROR_USER_DATA_NOT_RECOGNIZED;
        } catch (Exception e) {
            if (e.getMessage().equals("OperationNotAllowed"))
                resultCode = DIAMETER_ERROR_OPERATION_NOT_ALLOWED;
            if (e.getMessage().equals("SubscriberIncoherentData"))
                resultCode = DIAMETER_ERROR_USER_DATA_NOT_RECOGNIZED;
            if (e.getMessage().equals("SubscriberNotFound"))
                resultCode = DIAMETER_ERROR_USER_DATA_NOT_RECOGNIZED;
            if (e.getMessage().equals("ApplicationUnsupported"))
                resultCode = ResultCode.APPLICATION_UNSUPPORTED;
        }

        UserDataAnswer uda = new UserDataAnswerImpl((Request) udr.getMessage(), resultCode);
        AvpSet udaAvpSet = uda.getMessage().getAvps();

        if (resultCode == ResultCode.SUCCESS) {
            try {
                udaAvpSet.addAvp(Avp.USER_DATA_SH, userData, 10415, true, false, true);
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode +
                    " (SUCCESS)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            } catch (Exception e) {
                logger.info(">< Error generating UDA] User-Data-Answer", e);
            }
        }
        else if (resultCode == DIAMETER_ERROR_USER_DATA_NOT_RECOGNIZED) {
            try {
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode +
                    " (DIAMETER_ERROR_USER_DATA_NOT_RECOGNIZED)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }
        else if (resultCode == DIAMETER_ERROR_OPERATION_NOT_ALLOWED) {
            udaAvpSet.removeAvp(Avp.RESULT_CODE);
            udaAvpSet.addAvp(Avp.AUTH_SESSION_STATE, 0, 0, true, false, true);
            AvpSet experimentalResult = udaAvpSet.addGroupedAvp(Avp.EXPERIMENTAL_RESULT, true, false);
            experimentalResult.addAvp(Avp.EXPERIMENTAL_RESULT_CODE, DIAMETER_ERROR_OPERATION_NOT_ALLOWED, true, true);
            experimentalResult.addAvp(Avp.VENDOR_ID, 10415, true, false);
            try {
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with experimental result code:" + resultCode +
                    " (DIAMETER_ERROR_OPERATION_NOT_ALLOWED)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }
        else if (resultCode == DIAMETER_ERROR_USER_DATA_CANNOT_BE_READ) {
            try {
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode +
                    " (DIAMETER_ERROR_USER_DATA_CANNOT_BE_READ)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }
        else if (resultCode == DIAMETER_ERROR_USER_DATA_CANNOT_BE_MODIFIED) {
            try {
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode +
                    " (DIAMETER_ERROR_USER_DATA_CANNOT_BE_MODIFIED)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }
        else if (resultCode == DIAMETER_ERROR_USER_DATA_CANNOT_BE_NOTIFIED_ON_CHANGES) {
            try {
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode +
                    " (DIAMETER_ERROR_USER_DATA_CANNOT_BE_NOTIFIED_ON_CHANGES)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }
        else if (resultCode == DIAMETER_ERROR_TRANSPARENT_DATA_OUT_OF_SYNC) {
            try {
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode +
                    " (DIAMETER_ERROR_TRANSPARENT_DATA_OUT_OF_SYNC)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }
        else if (resultCode == DIAMETER_ERROR_SUBS_DATA_ABSENT) {
            try {
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode +
                    " (DIAMETER_ERROR_SUBS_DATA_ABSENT)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }
        else if (resultCode == DIAMETER_ERROR_NO_SUBSCRIPTION_TO_DATA) {
            try {
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode +
                    " (DIAMETER_ERROR_NO_SUBSCRIPTION_TO_DATA)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }
        else if (resultCode == DIAMETER_ERROR_DSAI_NOT_AVAILABLE) {
            try {
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode +
                    " (DIAMETER_ERROR_DSAI_NOT_AVAILABLE)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }
        else if (resultCode == DIAMETER_ERROR_IDENTITIES_DONT_MATCH) {
            try {
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode +
                    " (DIAMETER_ERROR_IDENTITIES_DONT_MATCH)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }
        else if (resultCode == DIAMETER_ERROR_TOO_MUCH_DATA) {
            try {
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode +
                    " (DIAMETER_ERROR_TOO_MUCH_DATA)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }
        else if (resultCode == DIAMETER_USER_DATA_NOT_AVAILABLE) {
            try {
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode +
                    " (DIAMETER_USER_DATA_NOT_AVAILABLE)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }
        else if (resultCode == DIAMETER_PRIOR_UPDATE_IN_PROGRESS) {
            try {
                logger.info("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode +
                    " (DIAMETER_PRIOR_UPDATE_IN_PROGRESS)");
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        } else {
            try {
                logger.warn("<> Sending [UDA] User-Data-Answer to " + udr.getOriginHost() + "@" +udr.getOriginRealm() + " with result code:" + resultCode);
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }
        session.sendUserDataAnswer(uda);
    }

    @Override
    public void doOtherEvent(AppSession session, AppRequestEvent request, AppAnswerEvent answer)
            throws InternalException, IllegalDiameterStateException, RouteException, OverloadException {
        logger.info("Diameter Sh Session Factory :: doOtherEvent :: appSession[{}], Request[{}], Answer[{}]",
            new Object[] { session, request, answer });
    }
}
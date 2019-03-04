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
import org.jdiameter.common.impl.app.sh.ShSessionFactoryImpl;
import org.jdiameter.common.impl.app.sh.UserDataAnswerImpl;
import org.jdiameter.server.impl.app.sh.ShServerSessionImpl;
import org.mobicents.servers.diameter.location.data.SubscriberInformation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final int DIAMETER_USER_DATA_NOT_AVAILABLE = 4101;
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
        String publicIdentity = "";

        if (logger.isInfoEnabled()) {
            logger.info("<> Processing [UDR] User-Data-Request for request [" + udr + "] session-id [" + session.getSessionId() +"]");
        }

        AvpSet udrAvpSet = udr.getMessage().getAvps();

        if (udrAvpSet.getAvp(Avp.USER_IDENTITY) != null) {
            try {
                if (udrAvpSet.getAvp(Avp.USER_IDENTITY).getGrouped().getAvp(Avp.MSISDN) != null)
                    msisdn = udrAvpSet.getAvp(Avp.USER_IDENTITY).getGrouped().getAvp(Avp.MSISDN).getUTF8String();
                if (udrAvpSet.getAvp(Avp.USER_IDENTITY).getGrouped().getAvp(Avp.PUBLIC_IDENTITY) != null)
                    publicIdentity = udrAvpSet.getAvp(Avp.USER_IDENTITY).getGrouped().getAvp(Avp.PUBLIC_IDENTITY).getUTF8String();
            } catch (AvpDataException e) {
                e.printStackTrace();
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("<> Generating [UDA] User-Data-Answer response data for MSISDN="+msisdn);
        }

        String userData = null;
        try {
            userData = subscriberInformation.getUserDataBySubscriber(msisdn);
            if (userData != null)
                resultCode = ResultCode.SUCCESS;
            else
                resultCode = DIAMETER_ERROR_USER_DATA_NOT_RECOGNIZED;
        } catch (Exception e) {
            if (e.getMessage().equals("SubscriberNotFound"))
                resultCode = DIAMETER_ERROR_USER_DATA_NOT_RECOGNIZED;
        }

        UserDataAnswer uda = new UserDataAnswerImpl((Request) udr.getMessage(), resultCode);
        AvpSet udaAvpSet = uda.getMessage().getAvps();

        if (resultCode == ResultCode.SUCCESS) {
            try {
                udaAvpSet.addAvp(Avp.USER_DATA_SH, userData, 10415, true, false, true);
            } catch (Exception e) {
                logger.info(">< Error generating UDA] User-Data-Answer", e);
            }
        }

        if (logger.isInfoEnabled()) {
            if (resultCode == DIAMETER_ERROR_USER_DATA_NOT_RECOGNIZED)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", DIAMETER_ERROR_USER_DATA_NOT_RECOGNIZED");
            else if (resultCode == DIAMETER_ERROR_OPERATION_NOT_ALLOWED)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", DIAMETER_ERROR_OPERATION_NOT_ALLOWED");
            else if (resultCode == DIAMETER_ERROR_USER_DATA_CANNOT_BE_READ)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", DIAMETER_ERROR_USER_DATA_CANNOT_BE_READ");
            else if (resultCode == DIAMETER_ERROR_USER_DATA_CANNOT_BE_MODIFIED)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", DIAMETER_ERROR_USER_DATA_CANNOT_BE_MODIFIED");
            else if (resultCode == DIAMETER_ERROR_USER_DATA_CANNOT_BE_NOTIFIED_ON_CHANGES)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", DIAMETER_ERROR_USER_DATA_CANNOT_BE_NOTIFIED_ON_CHANGES");
            else if (resultCode == DIAMETER_ERROR_TRANSPARENT_DATA_OUT_OF_SYNC)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", DIAMETER_ERROR_TRANSPARENT_DATA_OUT_OF_SYNC");
            else if (resultCode == DIAMETER_ERROR_SUBS_DATA_ABSENT)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", DIAMETER_ERROR_SUBS_DATA_ABSENT");
            else if (resultCode == DIAMETER_ERROR_NO_SUBSCRIPTION_TO_DATA)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", DIAMETER_ERROR_NO_SUBSCRIPTION_TO_DATA");
            else if (resultCode == DIAMETER_ERROR_DSAI_NOT_AVAILABLE)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", DIAMETER_ERROR_DSAI_NOT_AVAILABLE");
            else if (resultCode == DIAMETER_ERROR_IDENTITIES_DONT_MATCH)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", DIAMETER_ERROR_IDENTITIES_DONT_MATCH");
            else if (resultCode == DIAMETER_ERROR_TOO_MUCH_DATA)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", DIAMETER_ERROR_TOO_MUCH_DATA");
            else if (resultCode == DIAMETER_USER_DATA_NOT_AVAILABLE)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", DIAMETER_USER_DATA_NOT_AVAILABLE");
            else if (resultCode == DIAMETER_PRIOR_UPDATE_IN_PROGRESS)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", DIAMETER_PRIOR_UPDATE_IN_PROGRESS");
            else if (resultCode == ResultCode.UNABLE_TO_DELIVER)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", UNABLE_TO_DELIVER");
            else if (resultCode == ResultCode.SUCCESS)
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode+", SUCCESS");
            else
                logger.info("<> Sending [UDA] User-Data-Answer to GMLC with result code: "+resultCode);
        }

        session.sendUserDataAnswer(uda);
    }

    @Override
    public void doOtherEvent(AppSession session, AppRequestEvent request, AppAnswerEvent answer)
            throws InternalException, IllegalDiameterStateException, RouteException, OverloadException {

    }
}
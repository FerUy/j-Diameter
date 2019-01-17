/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a full listing
 * of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License, v. 2.0.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License,
 * v. 2.0 along with this distribution; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */
package org.mobicents.diameter.stack.functional.sh.base;

import org.jdiameter.api.Answer;
import org.jdiameter.api.Avp;
import org.jdiameter.api.AvpSet;
import org.jdiameter.api.IllegalDiameterStateException;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.NetworkReqListener;
import org.jdiameter.api.OverloadException;
import org.jdiameter.api.Request;
import org.jdiameter.api.RouteException;
import org.jdiameter.api.app.AppAnswerEvent;
import org.jdiameter.api.app.AppRequestEvent;
import org.jdiameter.api.app.AppSession;
import org.jdiameter.api.sh.ServerShSession;
import org.jdiameter.api.sh.events.ProfileUpdateRequest;
import org.jdiameter.api.sh.events.PushNotificationAnswer;
import org.jdiameter.api.sh.events.PushNotificationRequest;
import org.jdiameter.api.sh.events.SubscribeNotificationsRequest;
import org.jdiameter.api.sh.events.UserDataAnswer;
import org.jdiameter.api.sh.events.UserDataRequest;
import org.jdiameter.common.impl.app.sh.UserDataAnswerImpl;
import org.mobicents.diameter.stack.functional.Utils;
import org.mobicents.diameter.stack.functional.sh.AbstractShServer;

/**
 * Base implementation of Server
 *
 * @author <a href="mailto:brainslog@gmail.com"> Alexandre Mendonca </a>
 * @author <a href="mailto:baranowb@gmail.com"> Bartosz Baranowski </a>
 */
public class ServerUDR extends AbstractShServer {

  protected boolean sentSubscribeNotifications;
  protected boolean sentProfileUpdate;
  protected boolean sentUserDataAnswer;
  protected boolean sentPushNotification;
  protected boolean receivedSubscribeNotifications;
  protected boolean receivedProfileUpdate;
  protected boolean receivedUserDataRequest;
  protected boolean receivedPushNotification;

  protected UserDataRequest userDataRequest;

  // ------- send methods to trigger answer

  public void sendUserData() throws Exception {
    if (!this.receivedUserDataRequest || this.userDataRequest == null) {
      fail("Did not receive USER DATA or answer already sent.", null);
      throw new Exception("Did not receive USER DATA or answer already sent. Request: " + this.userDataRequest);
    }
    UserDataAnswer answer = new UserDataAnswerImpl((Request) this.userDataRequest.getMessage(), 2001);

    AvpSet reqSet = userDataRequest.getMessage().getAvps();

    AvpSet set = answer.getMessage().getAvps();
    set.removeAvp(Avp.DESTINATION_HOST);
    set.removeAvp(Avp.DESTINATION_REALM);
    set.addAvp(reqSet.getAvp(Avp.CC_REQUEST_TYPE), reqSet.getAvp(Avp.CC_REQUEST_NUMBER), reqSet.getAvp(Avp.AUTH_APPLICATION_ID));
    set.addAvp(Avp.AUTH_SESSION_STATE, 1);
    this.serverShSession.sendUserDataAnswer(answer);

    Utils.printMessage(log, super.stack.getDictionary(), answer.getMessage(), true);
    this.userDataRequest = null;
  }

  public void sendUserDataAnswer() throws Exception {
    if (!receivedUserDataRequest || userDataRequest == null) {
      fail("Did not receive RIR or answer already sent.", null);
      throw new Exception("Did not receive RIR or answer already sent. Request: " + this.userDataRequest);
    }

    UserDataAnswer uda = super.createUDA(userDataRequest, 2001);

    super.serverShSession.sendUserDataAnswer(uda);

    this.sentUserDataAnswer = true;
    userDataRequest = null;
    Utils.printMessage(log, super.stack.getDictionary(), uda.getMessage(), isSentUserDataAnswer());
  }

  // ------- initial, this will be triggered for first msg.

  /*
   * (non-Javadoc)
   *
   * @see org.jdiameter.api.NetworkReqListener#processRequest(org.jdiameter.api.Request)
   */
  @Override
  public Answer processRequest(Request request) {
    int code = request.getCommandCode();
    if (code != UserDataRequest.code) {
      fail("Received Request with code not used by Sh!. Code[" + request.getCommandCode() + "]", null);
      return null;
    }
    if (super.serverShSession == null) {
      try {
        super.serverShSession = this.sessionFactory.getNewAppSession(request.getSessionId(), getApplicationId(), ServerShSession.class, (Object) null);
        ((NetworkReqListener) this.serverShSession).processRequest(request);
      }
      catch (Exception e) {
        fail(null, e);
      }
    }
    else {
      // do fail?
      fail("Received Request in base listener, not in app specific!" + code, null);
    }
    return null;
  }

  // ------------- specific, app session listener.

  /*
   * (non-Javadoc)
   *
   * @see org.jdiameter.api.cca.ServerRoSessionListener#doOtherEvent(org.jdiameter.api.app.AppSession,
   * org.jdiameter.api.app.AppRequestEvent, org.jdiameter.api.app.AppAnswerEvent)
   */
  @Override
  public void doOtherEvent(AppSession session, AppRequestEvent request, AppAnswerEvent answer)
      throws InternalException, IllegalDiameterStateException, RouteException, OverloadException {
    fail("Received \"Other\" event, request[" + request + "], answer[" + answer + "], on session[" + session + "]", null);
  }

  @Override
  public void doSubscribeNotificationsRequestEvent(ServerShSession session, SubscribeNotificationsRequest request)
      throws InternalException, IllegalDiameterStateException, RouteException, OverloadException {
    this.receivedSubscribeNotifications = true;
    fail("Received \"SNR\" event, request[" + request + "], on session[" + session + "]", null);
  }

  @Override
  public void doProfileUpdateRequestEvent(ServerShSession session, ProfileUpdateRequest request)
      throws InternalException, IllegalDiameterStateException, RouteException, OverloadException {
    this.receivedProfileUpdate = true;
    fail("Received \"PUR\" event, request[" + request + "], on session[" + session + "]", null);
  }

  @Override
  public void doPushNotificationAnswerEvent(ServerShSession session, PushNotificationRequest request, PushNotificationAnswer answer) throws InternalException,
  IllegalDiameterStateException, RouteException, OverloadException {
    fail("Received \"PNA\" event, request[" + request + "], answer[" + answer + "], on session[" + session + "]", null);
    this.receivedPushNotification = true;
  }

  @Override
  public void doUserDataRequestEvent(ServerShSession session, UserDataRequest request)
      throws InternalException, IllegalDiameterStateException, RouteException, OverloadException {
    if (this.receivedUserDataRequest) {
      fail("Received USER DATA more than once!", null);
    }
    this.receivedUserDataRequest = true;
    this.userDataRequest = request;
  }

  @Override
  public void receivedSuccessMessage(Request request, Answer answer) {
    fail("Received \"SuccessMessage\" event, request[" + request + "], answer[" + answer + "]", null);
  }

  @Override
  public void timeoutExpired(Request request) {
    fail("Received \"Timoeout\" event, request[" + request + "]", null);
  }

  public boolean isSentSubscribeNotifications() {
    return sentSubscribeNotifications;
  }

  public boolean isSentProfileUpdate() {
    return sentProfileUpdate;
  }

  public boolean isSentUserDataAnswer() {
    return sentUserDataAnswer;
  }

  public boolean isSentPushNotification() {
    return sentPushNotification;
  }

  public boolean isReceiveSubscribeNotifications() {
    return receivedSubscribeNotifications;
  }

  public boolean isReceiveProfileUpdate() {
    return receivedProfileUpdate;
  }

  public boolean isReceiveUserData() {
    return receivedUserDataRequest;
  }

  public boolean isReceivePushNotification() {
    return receivedPushNotification;
  }


  @Override
  protected String getWildcardedPublicIdentity() {
    // 3GPP TS 29.172 v15.1.0 section 6.3.19
    String wpi = "sip:*@be-connect.us";
    return wpi;
  }

  @Override
  protected String getWildcardedIMPU() {
    // 3GPP TS 29.172 v15.1.0 section 6.3.20
    String wimpu = "tel:+598*";
    return wimpu;
  }

  @Override
  protected byte[] getUserData() {
    String userDataString = "121314151617181920";
    byte[] userData = userDataString.getBytes();
    return userData;
  }

  @Override
  protected long getOcFeatureVector() {
    long ocFeatureVector = 2L;
    return ocFeatureVector;
  }
}

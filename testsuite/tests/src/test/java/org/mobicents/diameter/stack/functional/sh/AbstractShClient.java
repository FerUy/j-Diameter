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
package org.mobicents.diameter.stack.functional.sh;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.Avp;
import org.jdiameter.api.AvpSet;
import org.jdiameter.api.IllegalDiameterStateException;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.Mode;
import org.jdiameter.api.sh.ClientShSession;
import org.jdiameter.api.sh.ClientShSessionListener;
import org.jdiameter.api.sh.ServerShSession;
import org.jdiameter.api.sh.events.UserDataRequest;
import org.jdiameter.common.impl.app.sh.ShSessionFactoryImpl;
import org.jdiameter.common.impl.app.sh.UserDataRequestImpl;
import org.mobicents.diameter.stack.functional.TBase;

/**
 *
 * @author <a href="mailto:brainslog@gmail.com"> Alexandre Mendonca </a>
 * @author <a href="mailto:baranowb@gmail.com"> Bartosz Baranowski </a>
 * @author <a href="mailto:fernando.mendioroz@gmail.com"> Fernando Mendioroz </a>
 */
public abstract class AbstractShClient extends TBase implements ClientShSessionListener {

  // NOTE: implementing NetworkReqListener since its required for stack to
  // know we support it... ech.

  protected ClientShSession clientShSession;

  public void init(InputStream configStream, String clientID) throws Exception {
    try {
      super.init(configStream, clientID, ApplicationId.createByAuthAppId(10415, 16777217));
      ShSessionFactoryImpl shSessionFactory = new ShSessionFactoryImpl(this.sessionFactory);
      sessionFactory.registerAppFacory(ServerShSession.class, shSessionFactory);
      sessionFactory.registerAppFacory(ClientShSession.class, shSessionFactory);

      shSessionFactory.setClientShSessionListener(this);

      this.clientShSession = this.sessionFactory
          .getNewAppSession(this.sessionFactory.getSessionId("xxTESTxx"), getApplicationId(), ClientShSession.class, (Object) null);
    }
    finally {
      try {
        configStream.close();
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  // ----------- delegate methods so

  public void start() throws IllegalDiameterStateException, InternalException {
    stack.start();
  }

  public void start(Mode mode, long timeOut, TimeUnit timeUnit) throws IllegalDiameterStateException, InternalException {
    stack.start(mode, timeOut, timeUnit);
  }

  public void stop(long timeOut, TimeUnit timeUnit, int disconnectCause) throws IllegalDiameterStateException, InternalException {
    stack.stop(timeOut, timeUnit, disconnectCause);
  }

  public void stop(int disconnectCause) {
    stack.stop(disconnectCause);
  }

  // ----------- conf parts

  public String getSessionId() {
    return this.clientShSession.getSessionId();
  }

  public ClientShSession getSession() {
    return this.clientShSession;
  }

  protected abstract String getPublicIdentity();
  protected abstract byte[] getMSISDN();
  protected abstract String getWildcardedPublicIdentity();
  protected abstract String getWildcardedIMPU();
  protected abstract String getServerName();
  protected abstract byte[] getServiceIndication();
  protected abstract int getDataReference();
  protected abstract int getIdentitySet();
  protected abstract int getRequestedDomain();
  protected abstract int getCurrentLocation();
  protected abstract byte[] getDSAITag();
  protected abstract int getSessionPriority();
  protected abstract String getUserName();
  protected abstract long getRequestedNodes();
  protected abstract int getServingNodeIndication();
  protected abstract int getPrePagingSupported();
  protected abstract int getLocalTimeZoneIndication();
  protected abstract long getUDRFlags();
  protected abstract byte[] getCallReferenceNumber();
  protected abstract byte[] getAsNumber();
  protected abstract long getOcFeatureVector();

  protected UserDataRequest createUDR(ClientShSession clientShSession) throws Exception {
    // ----------- 3GPP TS 29.329 v15.1.0  ----------- //
    /*
      6.1.1	User-Data-Request (UDR) Command
      The User-Data-Request (UDR) command, indicated by the Command-Code field set to 306 and the ‘R’ bit set in the Command Flags field,
      is sent by a Diameter client to a Diameter server in order to request user data.
      Message Format
        < User-Data -Request> ::=	< Diameter Header: 306, REQ, PXY, 16777217 >
                                        < Session-Id >
                                        [ DRMP ]
                                        { Vendor-Specific-Application-Id }
                                        { Auth-Session-State }
                                        { Origin-Host }
                                        { Origin-Realm }
                                        [ Destination-Host ]
                                        { Destination-Realm }
                                        *[ Supported-Features ]
                                        { User-Identity }
                                        [ Wildcarded-Public-Identity ]
                                        [ Wildcarded-IMPU ]
                                        [ Server-Name ]
                                        *[ Service-Indication ]
                                        *{ Data-Reference }
                                        *[ Identity-Set ]
                                        [ Requested-Domain ]
                                        [ Current-Location ]
                                        *[ DSAI-Tag ]
                                        [ Session-Priority ]
                                        [ User-Name ]
                                        [ Requested-Nodes ]
                                        [ Serving-Node-Indication ]
                                        [ Pre-paging-Supported ]
                                        [ Local-Time-Zone-Indication ]
                                        [ UDR-Flags ]
                                        [ Call-Reference-Info ]
                                        [ OC-Supported-Features ]
                                        *[ AVP ]
                                        *[ Proxy-Info ]
                                        *[ Route-Record ]
    */
    // Create LCSRoutingInfoRequest
    UserDataRequest udr = new UserDataRequestImpl(clientShSession.getSessions().get(0).createRequest(UserDataRequest.code, getApplicationId(),
        getServerRealmName()));

    AvpSet reqSet = udr.getMessage().getAvps();

    if (reqSet.getAvp(Avp.VENDOR_SPECIFIC_APPLICATION_ID) == null) {
      AvpSet vendorSpecificApplicationId = reqSet.addGroupedAvp(Avp.VENDOR_SPECIFIC_APPLICATION_ID, 0, false, false);
      // 1* [ Vendor-Id ]
      vendorSpecificApplicationId.addAvp(Avp.VENDOR_ID, getApplicationId().getVendorId(), true);
      // 0*1{ Auth-Application-Id }
      vendorSpecificApplicationId.addAvp(Avp.AUTH_APPLICATION_ID, getApplicationId().getAuthAppId(), true);
    }

    // { Auth-Session-State }
    if (reqSet.getAvp(Avp.AUTH_SESSION_STATE) == null) {
      reqSet.addAvp(Avp.AUTH_SESSION_STATE, 1);
    }

    // { Origin-Host }
    reqSet.removeAvp(Avp.ORIGIN_HOST);
    reqSet.addAvp(Avp.ORIGIN_HOST, getClientURI(), true);

    // { User-Identity }
    AvpSet userIdentity = reqSet.addGroupedAvp(Avp.USER_IDENTITY, 10415, true, false);
    String publicIdentity = getPublicIdentity();
    byte[] msisdn = getMSISDN();
    if (publicIdentity != null) {
      userIdentity.addAvp(Avp.PUBLIC_IDENTITY, publicIdentity, 10415, false, false, false);
    }
    if (msisdn != null) {
      userIdentity.addAvp(Avp.MSISDN, publicIdentity, 10415, true, false, true);
    }

    // [ Wildcarded-Public-Identity ]
    String wildcardedPublicIdentity = getWildcardedPublicIdentity();
    if (wildcardedPublicIdentity != null) {
      reqSet.addAvp(Avp.WILDCARDED_PUBLIC_IDENTITY, wildcardedPublicIdentity, 10415, false, false, false);
    }

    // [ Wildcarded-IMPU ]
    String wildcardedIMPU = getWildcardedIMPU();
    if (wildcardedIMPU != null) {
      reqSet.addAvp(Avp.WILDCARDED_IMPU, wildcardedIMPU, 10415, false, false, false);
    }

    // [ Server-Name ]
    String serverName = getServerName();
    if (serverName != null) {
      reqSet.addAvp(Avp.SERVER_NAME, serverName, 10415, false, false, false);
    }

    // [ Service-Indication ]
    byte[] serviceIndication = getServiceIndication();
    if (serviceIndication != null) {
      reqSet.addAvp(Avp.SERVICE_INDICATION, serviceIndication, 10415, false, false);
    }

    // { Data-Reference }
    int dataReference = getDataReference();
    if (dataReference != -1) {
      reqSet.addAvp(Avp.DATA_REFERENCE, dataReference, 10415, true, false);
    }

    // [ Identity-Set ]
    int identitySet = getIdentitySet();
    if (identitySet != -1) {
      reqSet.addAvp(Avp.IDENTITY_SET, identitySet, 10415, false, false);
    }

    // [ Requested-Domain ]
    int requestedDomain = getRequestedDomain();
    if (requestedDomain != -1) {
      reqSet.addAvp(Avp.REQUESTED_DOMAIN, requestedDomain, 10415, false, false);
    }

    // [ Current-Location ]
    int currentLocation = getCurrentLocation();
    if (currentLocation != -1) {
      reqSet.addAvp(Avp.CURRENT_LOCATION, currentLocation, 10415, false, false);
    }

    // [ DSAI-Tag ]
    byte[] dsaiTag = getDSAITag();
    if (dsaiTag != null) {
      reqSet.addAvp(Avp.DSAI_TAG, dsaiTag, 10415, false, true);
    }

    // [ Session-Priority ]
    int sessionPriority = getSessionPriority();
    if (sessionPriority != -1) {
      reqSet.addAvp(Avp.SESSION_PRIORITY, sessionPriority, 10415, false, false);
    }

    // [ User-Name ]
    String userName = getUserName();
    if (userName != null) {
      reqSet.addAvp(Avp.USER_NAME, userName, 0, true, false, false);
    }

    // [ Requested-Nodes ]
    long requestedNodes = getRequestedNodes();
    if (requestedNodes != -1) {
      reqSet.addAvp(Avp.REQUESTED_NODES, requestedNodes, 10415, false, false, true);
    }

    // [ Serving-Node-Indication ]
    int servingNodeIndication = getServingNodeIndication();
    if (servingNodeIndication != -1) {
      reqSet.addAvp(Avp.SERVING_NODE_INDICATION, servingNodeIndication, 10415, false, false);
    }

    // [ Pre-paging-Supported ]
    int prepagingSupported = getPrePagingSupported();
    if (prepagingSupported != -1) {
      reqSet.addAvp(Avp.PRE_PAGING_SUPPORTED, prepagingSupported, 10415, false, false);
    }

    // [ Local-Time-Zone-Indication ]
    int localTimeZoneIndication = getLocalTimeZoneIndication();
    if (localTimeZoneIndication != -1) {
      reqSet.addAvp(Avp.LOCAL_TIME_ZONE_INDICATION, localTimeZoneIndication, 10415, false, false);
    }

    // [ UDR-Flags ]
    long udrFlags = getUDRFlags();
    if (udrFlags != -1) {
      reqSet.addAvp(Avp.UDR_FLAGS, udrFlags, 10415, false, false, true);
    }

    // [ Call-Reference-Info ]
    AvpSet callReferenceInfo = reqSet.addGroupedAvp(Avp.CALL_REFERENCE_INFO, 10415, true, false);
    byte[] callReferenceNumber = getCallReferenceNumber();
    if (callReferenceNumber != null) {
      callReferenceInfo.addAvp(Avp.CALL_REFERENCE_NUMBER, callReferenceNumber, 10415, false, false);
    }
    byte[] asNumber = getAsNumber();
    if (asNumber != null) {
      callReferenceInfo.addAvp(Avp.AS_NUMBER, asNumber, 10415, false, false);
    }

    // [ OC-Supported-Features ]
    AvpSet ocSupportedFeatures = reqSet.addGroupedAvp(Avp.OC_SUPPORTED_FEATURES, 0, true, false);
    long ocFeaturesVector = getOcFeatureVector();
    if (ocFeaturesVector != -1) {
      ocSupportedFeatures.addAvp(Avp.OC_FEATURE_VECTOR, ocFeaturesVector, false, false, false);
    }

    return udr;
  }






}

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
import org.jdiameter.api.Request;
import org.jdiameter.api.sh.ClientShSession;
import org.jdiameter.api.sh.ServerShSession;
import org.jdiameter.api.sh.ServerShSessionListener;
import org.jdiameter.api.sh.events.UserDataAnswer;
import org.jdiameter.api.sh.events.UserDataRequest;
import org.jdiameter.common.impl.app.sh.ShSessionFactoryImpl;
import org.jdiameter.common.impl.app.sh.UserDataAnswerImpl;
import org.mobicents.diameter.stack.functional.TBase;

/**
 *
 * @author <a href="mailto:brainslog@gmail.com"> Alexandre Mendonca </a>
 * @author <a href="mailto:baranowb@gmail.com"> Bartosz Baranowski </a>
 * @author <a href="mailto:fernando.mendioroz@gmail.com"> Fernando Mendioroz </a>
 */
public abstract class AbstractShServer extends TBase implements ServerShSessionListener {

  // NOTE: implementing NetworkReqListener since its required for stack to
  // know we support it... ech.

  protected ServerShSession serverShSession;

  public void init(InputStream configStream, String clientID) throws Exception {
    try {
      super.init(configStream, clientID, ApplicationId.createByAuthAppId(10415, 16777217));
      ShSessionFactoryImpl shSessionFactory = new ShSessionFactoryImpl(this.sessionFactory);
      sessionFactory.registerAppFacory(ServerShSession.class, shSessionFactory);
      sessionFactory.registerAppFacory(ClientShSession.class, shSessionFactory);
      shSessionFactory.setServerShSessionListener(this);
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

  public String getSessionId() {
    return this.serverShSession.getSessionId();
  }

  public ServerShSession getSession() {
    return this.serverShSession;
  }


  protected abstract String getWildcardedPublicIdentity();
  protected abstract String getWildcardedIMPU();
  protected abstract byte[] getUserData();
  protected abstract long getOcFeatureVector();

  public UserDataAnswer createUDA(UserDataRequest udr, long resultCode) throws Exception {

    // ----------- 3GPP TS 29.329 v15.1.0  ----------- //
  /*
    6.1.2	User-Data-Answer (UDA) Command
        The User-Data-Answer (UDA) command, indicated by the Command-Code field set to 306 and the ‘R’ bit cleared in the Command Flags field, is sent by a server in response to the User-Data-Request command. The Experimental-Result AVP may contain one of the values defined in section 6.2 or in 3GPP TS 29.229 [6].
        Message Format
        < User-Data-Answer > ::=		< Diameter Header: 306, PXY, 16777217 >
        < Session-Id >
										[ DRMP ]
                                        { Vendor-Specific-Application-Id }
                                        [ Result-Code ]
                                        [ Experimental-Result ]
                                        { Auth-Session-State }
                                        { Origin-Host }
                                        { Origin-Realm }
                                        *[ Supported-Features ]
                                        [ Wildcarded-Public-Identity ]
                                        [ Wildcarded-IMPU ]
                                        [ User-Data ]
                                        [ OC-Supported-Features ]
                                        [ OC-OLR ]
                                        *[ Load ]
                                        *[ AVP ]
                                        [ Failed-AVP ]
                                        *[ Proxy-Info ]
									    *[ Route-Record ]
  */
  UserDataAnswer uda = new UserDataAnswerImpl((Request) udr.getMessage(), resultCode);

  AvpSet reqSet = udr.getMessage().getAvps();
  AvpSet set = uda.getMessage().getAvps();
  set.removeAvp(Avp.DESTINATION_HOST);
  set.removeAvp(Avp.DESTINATION_REALM);
  set.addAvp(reqSet.getAvp(Avp.AUTH_APPLICATION_ID));

  // { Vendor-Specific-Application-Id }
  if (set.getAvp(Avp.VENDOR_SPECIFIC_APPLICATION_ID) == null) {
    AvpSet vendorSpecificApplicationId = set.addGroupedAvp(Avp.VENDOR_SPECIFIC_APPLICATION_ID, 0, false, false);
    // 1* [ Vendor-Id ]
    vendorSpecificApplicationId.addAvp(Avp.VENDOR_ID, getApplicationId().getVendorId(), true);
    // 0*1{ Auth-Application-Id }
    vendorSpecificApplicationId.addAvp(Avp.AUTH_APPLICATION_ID, getApplicationId().getAuthAppId(), true);
  }
  // [ Result-Code ]
  // [ Experimental-Result ]
  // { Auth-Session-State }
  if (set.getAvp(Avp.AUTH_SESSION_STATE) == null) {
    set.addAvp(Avp.AUTH_SESSION_STATE, 1);
  }

  // [ Wildcarded-Public-Identity ]
  String wildcardedPublicIdentity = getWildcardedPublicIdentity();
  if (wildcardedPublicIdentity != null) {
    set.addAvp(Avp.WILDCARDED_PUBLIC_IDENTITY, wildcardedPublicIdentity, 10415, false, false, false);
  }

  // [ Wildcarded-IMPU ]
  String wildcardedIMPU = getWildcardedIMPU();
  if (wildcardedIMPU != null) {
    set.addAvp(Avp.WILDCARDED_IMPU, wildcardedIMPU, 10415, false, false, false);
  }

  // [ User-Data ]
  byte [] userData = getUserData();
  if (userData != null) {
    set.addAvp(Avp.USER_DATA_SH, userData, 10415, true, false);
  }

  // [ OC-Supported-Features ]
  AvpSet ocSupportedFeatures = set.addGroupedAvp(Avp.OC_SUPPORTED_FEATURES, 0, true, false);
  long ocFeaturesVector = getOcFeatureVector();
  if (ocFeaturesVector != -1) {
    ocSupportedFeatures.addAvp(Avp.OC_FEATURE_VECTOR, ocFeaturesVector, false, false, false);
  }

  return uda;

  }

}

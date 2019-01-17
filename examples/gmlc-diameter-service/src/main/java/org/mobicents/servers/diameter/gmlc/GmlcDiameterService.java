package org.mobicents.servers.diameter.gmlc;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.IllegalDiameterStateException;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.Mode;
import org.jdiameter.api.Network;
import org.jdiameter.api.OverloadException;
import org.jdiameter.api.Peer;
import org.jdiameter.api.RouteException;
import org.jdiameter.api.slg.ClientSLgSession;
import org.jdiameter.api.slg.ServerSLgSession;
import org.jdiameter.api.slh.ClientSLhSession;
import org.jdiameter.api.slh.ServerSLhSession;
import org.jdiameter.client.api.ISessionFactory;
import org.mobicents.diameter.dictionary.AvpDictionary;
import org.mobicents.servers.diameter.gmlc.points.SLhReferencePoint;
import org.mobicents.servers.diameter.gmlc.queue.GmlcConnector;
import org.mobicents.servers.diameter.gmlc.queue.IGmlcSubscriber;
import org.mobicents.servers.diameter.gmlc.points.SLgReferencePoint;
import org.mobicents.servers.diameter.gmlc.queue.data.LocationInformation;
import org.mobicents.servers.diameter.utils.StackCreator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BeConnect Gmlc Diameter Service.
 *
 * @author <a href="mailto:aferreiraguido@gmail.com"> Alejandro Ferreira Guido </a>
 */
public class GmlcDiameterService implements IGmlcSubscriber {

    private static final Logger logger = LoggerFactory.getLogger(GmlcDiameterService.class);

    private SLgReferencePoint slgMobilityManagementEntity = new SLgReferencePoint(this);
    private SLhReferencePoint slhHomeSubscriberServer = new SLhReferencePoint(this);

    private GmlcDiameterSession activeSessions = new GmlcDiameterSession();

    private static final Object[] EMPTY_ARRAY = new Object[]{};

    public static void main(String[] args) throws Exception {
        boolean doomsday = true;

        GmlcDiameterService gmlcDiameterService = new GmlcDiameterService();

        Scanner scanner = new Scanner(System.in);
        while (doomsday) {
            try {
                String command = scanner.next();
                if (command.equals("exit")) {
                    gmlcDiameterService.delete();
                    doomsday = false;
                } else if (command.equals("?") || command.equals("help")) {
                    System.out.println("exit, help and ? are the only commands");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    GmlcConnector gmlcConnector = null;
    StackCreator stackCreator = null;

    public GmlcDiameterService() throws Exception {
        super();

        gmlcConnector = new GmlcConnector(false, this);

        AvpDictionary.INSTANCE.parseDictionary(this.getClass().getClassLoader().getResourceAsStream("dictionary.xml"));

        try {
            String config = readFile(this.getClass().getClassLoader().getResourceAsStream("config-server.xml"));
            this.stackCreator = new StackCreator(config, null, null, "GmlcDiameterService", true);

            Network network = this.stackCreator.unwrap(Network.class);
            network.addNetworkReqListener(slgMobilityManagementEntity,
                    ApplicationId.createByAuthAppId(10415L, slgMobilityManagementEntity.getApplicationId()));
            network.addNetworkReqListener(slgMobilityManagementEntity,
                    ApplicationId.createByAuthAppId(0, slgMobilityManagementEntity.getApplicationId()));

            network.addNetworkReqListener(slhHomeSubscriberServer,
                    ApplicationId.createByAuthAppId(10415L, slhHomeSubscriberServer.getApplicationId()));
            network.addNetworkReqListener(slhHomeSubscriberServer,
                    ApplicationId.createByAuthAppId(0, slhHomeSubscriberServer.getApplicationId()));

            this.stackCreator.start(Mode.ALL_PEERS, 30000, TimeUnit.MILLISECONDS);

            printLogo();

            ISessionFactory sessionFactory = (ISessionFactory) stackCreator.getSessionFactory();

            slgMobilityManagementEntity.init(sessionFactory);
            sessionFactory.registerAppFacory(ServerSLgSession.class, slgMobilityManagementEntity);
            sessionFactory.registerAppFacory(ClientSLgSession.class, slgMobilityManagementEntity);

            slhHomeSubscriberServer.init(sessionFactory);
            sessionFactory.registerAppFacory(ServerSLhSession.class, slhHomeSubscriberServer);
            sessionFactory.registerAppFacory(ClientSLhSession.class, slhHomeSubscriberServer);

        } catch (Exception e) {
            logger.error("Failure initializing Be-Connect Diameter Slg/Slh Server Simulator", e);
        }
    }

    public void delete() {
        this.stackCreator.destroy();
    }

    private void printLogo() {
        if (logger.isInfoEnabled()) {
            Properties sysProps = System.getProperties();

            String osLine = sysProps.getProperty("os.name") + "/" + sysProps.getProperty("os.arch");
            String javaLine = sysProps.getProperty("java.vm.vendor") + " " + sysProps.getProperty("java.vm.name") + " " + sysProps.getProperty("java.vm.version");

            Peer localPeer = stackCreator.getMetaData().getLocalPeer();

            String diameterLine = localPeer.getProductName() + " (" + localPeer.getUri() + " @ " + localPeer.getRealmName() + ")";

            logger.info("===============================================================================");
            logger.info("");
            logger.info("== BeConnect Gmlc Diameter Slg/Slh Service (" + osLine + ")");
            logger.info("");
            logger.info("== " + javaLine);
            logger.info("");
            logger.info("== " + diameterLine);
            logger.info("");
            logger.info("===============================================================================");
        }
    }

    private static String readFile(InputStream is) throws IOException {
        BufferedInputStream bin = new BufferedInputStream(is);
        StringBuilder sb = new StringBuilder();
        byte[] contents = new byte[1024];
        String strFileContents;
        int bytesRead = 0;


        while ((bytesRead = bin.read(contents)) != -1) {
            strFileContents = new String(contents, 0, bytesRead);
            sb.append(strFileContents);
        }

        return sb.toString();
    }

    public void notificationReceived(LocationInformation locationInformation) {
        logger.info("New message received '" + locationInformation + "'");

        try {
            if (locationInformation.getPrimitive().equals(LocationInformation.LocationPrimitive.RoutingInformationRequest)) {
                locationInformation.generateSessionId();
                activeSessions.saveSession(locationInformation);
                slhHomeSubscriberServer.performRoutingInfoRequest(locationInformation);
            } else if (locationInformation.getPrimitive().equals(LocationInformation.LocationPrimitive.ProvideLocationRequest)) {
                slgMobilityManagementEntity.performProvideLocationRequest(locationInformation);
            } else if (locationInformation.getPrimitive().equals(LocationInformation.LocationPrimitive.ProvideLocationAnswer)) {
                gmlcConnector.send(locationInformation);
            } else if (locationInformation.getPrimitive().equals(LocationInformation.LocationPrimitive.LocationReportRequest)
                    || locationInformation.getPrimitive().equals(LocationInformation.LocationPrimitive.RoutingInformationError)) {
                gmlcConnector.send(locationInformation);
            }
        } catch (InternalException e) {
            e.printStackTrace();
        } catch (RouteException e) {
            e.printStackTrace();
        } catch (OverloadException e) {
            e.printStackTrace();
        } catch (IllegalDiameterStateException e) {
            e.printStackTrace();
        }
    }

}
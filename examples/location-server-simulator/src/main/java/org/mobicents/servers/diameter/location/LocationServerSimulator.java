package org.mobicents.servers.diameter.location;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.IllegalDiameterStateException;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.Mode;
import org.jdiameter.api.Network;
import org.jdiameter.api.OverloadException;
import org.jdiameter.api.Peer;
import org.jdiameter.api.PeerTable;
import org.jdiameter.api.RouteException;
import org.jdiameter.api.SessionFactory;
import org.jdiameter.api.sh.ClientShSession;
import org.jdiameter.api.sh.ServerShSession;
import org.jdiameter.api.slg.ClientSLgSession;
import org.jdiameter.api.slg.ServerSLgSession;
import org.jdiameter.api.slh.ClientSLhSession;
import org.jdiameter.api.slh.ServerSLhSession;
import org.jdiameter.client.api.ISessionFactory;
import org.jdiameter.common.impl.app.sh.ShSession;
import org.jdiameter.common.impl.app.sh.ShSessionFactoryImpl;
import org.mobicents.diameter.dictionary.AvpDictionary;
import org.mobicents.servers.diameter.location.data.SubscriberElement;
import org.mobicents.servers.diameter.location.data.SubscriberInformation;
import org.mobicents.servers.diameter.location.points.SLgReferencePoint;
import org.mobicents.servers.diameter.location.points.SLhReferencePoint;
import org.mobicents.servers.diameter.location.points.ShReferencePoint;
import org.mobicents.servers.diameter.utils.StackCreator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * BeConnect Diameter Location Server Simulator.
 *
 * @author <a href="mailto:aferreiraguido@gmail.com"> Alejandro Ferreira Guido </a>
 * @author <a href="mailto:fernando.mendioroz@gmail.com"> Fernando Mendioroz </a>
 */
public class LocationServerSimulator {

    private static final Logger logger = LoggerFactory.getLogger(LocationServerSimulator.class);

    private SubscriberInformation subscriberInformation;

    private SLgReferencePoint slgMobilityManagementEntity;
    private SLhReferencePoint slhHomeSubscriberServer;
    private ShReferencePoint shHomeSubscriberServer;
    private SessionFactory sessionFactory;

    private static final Object[] EMPTY_ARRAY = new Object[]{};

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        boolean doomsday = true;

        LocationServerSimulator locationServerSimulator = new LocationServerSimulator();

        Scanner scanner = new Scanner(System.in);
        while (doomsday) {
            try {
                String command = scanner.nextLine();
                if (command.equals("exit")) {
                    locationServerSimulator.delete();
                    doomsday = false;
                } else if (command.startsWith("lrr ")) {
                    locationServerSimulator.sendLocationReportRequest(command);
                } else if (command.equals("?") || command.equals("help")) {
                    System.out.println("lrr <msisdn> <type> <ref-num>, exit and help are the only commands");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void delete() {
        this.stackCreator.destroy();
    }

    StackCreator stackCreator = null;

    public LocationServerSimulator() throws Exception {
        super();

        AvpDictionary.INSTANCE.parseDictionary(this.getClass().getClassLoader().getResourceAsStream("dictionary.xml"));

        try {
            subscriberInformation = SubscriberInformation.load();

            slgMobilityManagementEntity = new SLgReferencePoint(subscriberInformation);
            slhHomeSubscriberServer = new SLhReferencePoint(subscriberInformation);

            String serverXmlConfigurationFile = System.getProperty("user.dir") + "/config-server.xml";
            InputStream xmlConfigurationReader;
            File file = new File(serverXmlConfigurationFile);
            if (file.exists()) {
                logger.info("Load jDiameter configuration from '" + serverXmlConfigurationFile + "'");
                xmlConfigurationReader = new FileInputStream(file);
            } else {
                logger.info("Load jDiameter configuration from internal 'resources/config-server.xml'");
                xmlConfigurationReader = this.getClass().getClassLoader().getResourceAsStream("config-server.xml");
            }

            String config = readFile(xmlConfigurationReader);
            this.stackCreator = new StackCreator(config, null, null, "LocationServerSimulator", true);

            Network network = this.stackCreator.unwrap(Network.class);
            network.addNetworkReqListener(slgMobilityManagementEntity,
                    ApplicationId.createByAuthAppId(10415L, slgMobilityManagementEntity.getApplicationId()));
            network.addNetworkReqListener(slhHomeSubscriberServer,
                    ApplicationId.createByAuthAppId(10415L, slhHomeSubscriberServer.getApplicationId()));

            this.stackCreator.start(Mode.ALL_PEERS, 30000, TimeUnit.MILLISECONDS);

            printLogo();

            ISessionFactory sessionFactory = (ISessionFactory) stackCreator.getSessionFactory();

            slgMobilityManagementEntity.init(sessionFactory);
            sessionFactory.registerAppFacory(ServerSLgSession.class, slgMobilityManagementEntity);

            slhHomeSubscriberServer.init(sessionFactory);
            sessionFactory.registerAppFacory(ServerSLhSession.class, slhHomeSubscriberServer);

            shHomeSubscriberServer = new ShReferencePoint(sessionFactory, subscriberInformation);
            sessionFactory.registerAppFacory(ServerShSession.class, shHomeSubscriberServer);
            network.addNetworkReqListener(shHomeSubscriberServer,
                ApplicationId.createByAuthAppId(10415L, shHomeSubscriberServer.getApplicationId()));
        } catch (Exception e) {
            logger.error("Failure initializing be-connect diameter Sh/SLh/SLg server simulator", e);
        }
    }

    public void sendLocationReportRequest(String command)
            throws InternalException, OverloadException, IllegalDiameterStateException, RouteException {

        String[] commandParameter = command.split(" ");

        String msisdn = commandParameter[1];
        Integer locationEventType = Integer.parseInt(commandParameter[2]);    // MO_LR(2)
        String lcsReferenceNumber = commandParameter[3];

        this.slgMobilityManagementEntity.sendLocationReportRequest(msisdn, locationEventType, lcsReferenceNumber);
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
            logger.info("==  be-connect diameter Sh/SLh/SLg server simulator (" + osLine + ")");
            logger.info("");
            logger.info("==  " + javaLine);
            logger.info("");
            logger.info("==  " + diameterLine);
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
}
package org.mobicents.servers.diameter.location;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.IllegalDiameterStateException;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.Mode;
import org.jdiameter.api.Network;
import org.jdiameter.api.OverloadException;
import org.jdiameter.api.Peer;
import org.jdiameter.api.RouteException;
import org.jdiameter.api.SessionFactory;
import org.jdiameter.api.sh.ServerShSession;
import org.jdiameter.api.slg.ServerSLgSession;
import org.jdiameter.api.slh.ServerSLhSession;
import org.jdiameter.client.api.ISessionFactory;
import org.mobicents.diameter.dictionary.AvpDictionary;
import org.mobicents.servers.diameter.location.data.SubscriberInformation;
import org.mobicents.servers.diameter.location.points.SLgReferencePoint;
import org.mobicents.servers.diameter.location.points.SLhReferencePoint;
import org.mobicents.servers.diameter.location.points.ShReferencePoint;
import org.mobicents.servers.diameter.utils.StackCreator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.RouteImpl;

import static spark.Spark.*;

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
        // Parse command line options
        Options options = new Options();
        options.addOption(new Option("r", "rest", false, "run simulator as a RESTful service"));
        options.addOption(new Option("p", "port", true, "run simulator on defined port"));

        CommandLineParser commandLineParser = new DefaultParser();
        CommandLine commandLine;
        try {
            commandLine = commandLineParser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            return;
        }

        // start location server
        LocationServerSimulator locationServerSimulator = new LocationServerSimulator();

        // define RESTful API routes if background mode selected
        if (commandLine.hasOption("rest")) {
            if (commandLine.getOptionValue("port") != null)
                try {
                    port(Integer.parseInt(commandLine.getOptionValue("port")));
                } catch (Exception e) {
                    System.out.println("invalid port value passed on -port");
                }

            /*
                RESTful API route for LocationReportRequest

                PROTO:      HTTP-GET
                URL:        http://localhost:4567/lrr?msisdn={MSISDN}&locationEvent={LOCATION_EVENT_TYPE}&lcsReferenceNumber={REFERENCE_NUMBER}
                ARGUMENTS {
                    MSISDN[IntegerAsString],
                    LOCATION_EVENT_TYPE[Integer] {
                        EMERGENCY_CALL_ORIGINATION(0)
                        EMERGENCY_CALL_RELEASE(1)
                        MO_LR(2)
                        EMERGENCY_CALL_HANDOVER(3)
                        DEFERRED_MT_LR_RESPONSE(4)
                        DEFERRED_MO_LR_TTTP_INITIATION(5)
                        DELAYED_LOCATION_REPORTING(6)
                    },
                    REFERENCE_NUMBER[IntegerAsString]
                }
                Examples:
                curl -X GET 127.0.0.1:4567/lrr?msisdn=59899077937\&locationEvent=4\&lcsReferenceNumber=31
                Web browser
                http://localhost:4567/lrr?msisdn=573195897484&locationEvent=2&lcsReferenceNumber=281

             */
            get("/lrr", new RouteImpl("/lrr") {
                @Override
                public Object handle(Request request, Response response) throws Exception {
                    Boolean isImsi = false;
                    String subscriberIdentity = request.queryParams("msisdn");
                    if (subscriberIdentity == null) {
                        subscriberIdentity = request.queryParams("imsi");
                        isImsi = true;
                    }
                    String locationEvent = request.queryParams("locationEvent");
                    String lcsReferenceNumber = request.queryParams("lcsReferenceNumber");
                    return locationServerSimulator.sendLocationReportRequest(subscriberIdentity, locationEvent, lcsReferenceNumber, isImsi);
                }
            });

            return;
        }

        // default interactive console mode
        Scanner scanner = new Scanner(System.in);
        while (true) {
            try {
                String command = scanner.nextLine();
                if (command.equals("exit")) {
                    break;
                } else if (command.startsWith("lrr ")) {
                    locationServerSimulator.sendLocationReportRequest(command);
                } else if (command.equals("?") || command.equals("help")) {
                    System.out.println("lrr <msisdn> <imsi> <type> <ref-num>, exit and help are the only commands");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.exit(0);
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

        String subscriberIdentity = commandParameter[1];
        Integer locationEventType = Integer.parseInt(commandParameter[2]);    // MO_LR(2)
        String lcsReferenceNumber = commandParameter[3];

        this.slgMobilityManagementEntity.sendLocationReportRequest(subscriberIdentity, locationEventType, lcsReferenceNumber, false);
    }

    public String sendLocationReportRequest(String subscriberIdentity, String locationEvent, String lcsReferenceNumber, Boolean isImsi) {
        String result = "\nLRR sent successfully!\n";
        try {
            Integer locationEventType = Integer.parseInt(locationEvent);
            this.slgMobilityManagementEntity.sendLocationReportRequest(subscriberIdentity, locationEventType, lcsReferenceNumber, isImsi);
        } catch (Exception e) {
            result = String.format("\nLRR caused an exception '%s' - not sent!\n", e.getMessage());
        }

        return result;
    }

    private void printLogo() {
        if (logger.isInfoEnabled()) {
            Properties sysProps = System.getProperties();

            String osLine = sysProps.getProperty("os.name") + "/" + sysProps.getProperty("os.arch");
            String javaLine = sysProps.getProperty("java.vm.vendor") + " " + sysProps.getProperty("java.vm.name") + " " + sysProps.getProperty("java.vm.version");

            Peer localPeer = stackCreator.getMetaData().getLocalPeer();

            String diameterLine = localPeer.getProductName() + " (" + localPeer.getUri() + " @ " + localPeer.getRealmName() + ")";

            logger.info("||==============================================================================||");
            logger.info("||                                                                            	||");
            logger.info("||  Diameter SLh/SLg/Sh LTE/IMS Location Services Simulator (" + osLine + ")");
            logger.info("||                                                        		       	||");
            logger.info("||  " + javaLine);
            logger.info("||                                                                            	||");
            logger.info("||  " + diameterLine);
            logger.info("||                                                                            	||");
            logger.info("||==============================================================================||");
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

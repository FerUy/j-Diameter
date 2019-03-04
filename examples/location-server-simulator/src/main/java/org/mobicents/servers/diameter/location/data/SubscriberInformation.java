package org.mobicents.servers.diameter.location.data;

import com.google.gson.Gson;
import org.mobicents.servers.diameter.location.LocationServerSimulator;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * @author <a href="mailto:aferreiraguido@gmail.com"> Alejandro Ferreira Guido </a>
 * @author <a href="mailto:fernando.mendioroz@gmail.com"> Fernando Mendioroz </a>
 */
public class SubscriberInformation {

    private static final Logger logger = LoggerFactory.getLogger(LocationServerSimulator.class);
    private static String subscriberLocationDataFilename = "subscriber-location-data.json";

    public ArrayList<SubscriberElement> subscribers = new ArrayList<SubscriberElement>();

    SubscriberInformation() {
    }

    public static SubscriberInformation load() throws FileNotFoundException {
        try {
            String localSubscriberDataFullname = System.getProperty("user.dir") + "/" + subscriberLocationDataFilename;

            File file = new File(localSubscriberDataFullname);
            BufferedReader bufferedReader;
            if (file.exists()) {
                logger.info("Trying to load subscribers from '" + localSubscriberDataFullname + "' local file.");
                bufferedReader = new BufferedReader(new FileReader(file));
            } else {
                logger.info("Loading subscribers from internal 'resources/" + subscriberLocationDataFilename + "' file.");
                ClassLoader classLoader = SubscriberInformation.class.getClassLoader();
                InputStream inputStream = classLoader.getResourceAsStream(subscriberLocationDataFilename);
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            }

            SubscriberInformation subscriberInformation = new Gson().fromJson(bufferedReader, SubscriberInformation.class);
            logger.info("Loaded " + subscriberInformation.subscribers.size() + " records from location subscriber file.");

            return subscriberInformation;
        } catch (Exception e){
            logger.warn("Subscriber location information load error!", e);
        }

        return new SubscriberInformation();
    }

    public SubscriberElement getElementBySubscriber(String imsi, String msisdn) throws Exception {
        for (SubscriberElement subscriber: subscribers) {
            if (subscriber.imsi.equals(imsi) || subscriber.msisdn.equals(msisdn)) {
                if ((imsi == null || imsi.length() == 0) && subscriber.msisdn.equals(msisdn)) {
                    return subscriber;
                } else if ((msisdn == null || msisdn.length() == 0) && subscriber.imsi.equals(imsi)) {
                    return subscriber;
                } else if (subscriber.imsi.equals(imsi) && subscriber.msisdn.equals(msisdn)) {
                    return subscriber;
                } else {
                    throw new Exception("SubscriberCoherentData");
                }
            }
        }
        throw new Exception("SubscriberNotFound");
    }

    public String getUserDataBySubscriber(String msisdn) throws Exception {
        try {
            String localSubscriberUserDataFile = System.getProperty("user.dir") + "/sh-user-data/" + msisdn + ".xml";

            File file = new File(localSubscriberUserDataFile);
            BufferedReader bufferedReader;
            if (file.exists()) {
                logger.info("Loading subscriber user data from '" + localSubscriberUserDataFile + "' local file.");
                bufferedReader = new BufferedReader(new FileReader(file));
            } else {
                localSubscriberUserDataFile = "sh-user-data/" + msisdn + ".xml";
                logger.info("Loading subscriber user data from internal 'resources/" + localSubscriberUserDataFile + "' file.");
                ClassLoader classLoader = SubscriberInformation.class.getClassLoader();
                InputStream inputStream = classLoader.getResourceAsStream(localSubscriberUserDataFile);
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            }

            StringBuffer subscriberUserDataBuffer = new StringBuffer();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                subscriberUserDataBuffer.append(line).append("\n");
            }
            logger.info("Loaded " + subscriberUserDataBuffer.length() + " bytes from subscriber user data file.");

            return subscriberUserDataBuffer.toString();
        } catch (Exception e) {
            logger.warn("Subscriber information load error, not found!");
            throw new Exception("SubscriberNotFound");
        }
        //throw new Exception("SubscriberNotFound");
    }

}

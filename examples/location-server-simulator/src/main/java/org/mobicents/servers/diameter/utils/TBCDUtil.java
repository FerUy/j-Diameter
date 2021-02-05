package org.mobicents.servers.diameter.utils;

import org.mobicents.protocols.ss7.map.api.MAPException;
import org.mobicents.protocols.ss7.map.primitives.CellGlobalIdOrServiceAreaIdFixedLengthImpl;
import org.mobicents.protocols.ss7.map.service.mobility.subscriberInformation.EUtranCgiImpl;
import org.mobicents.protocols.ss7.map.service.mobility.subscriberInformation.TAIdImpl;

import static org.mobicents.servers.diameter.utils.byteUtils.bytesToHex;

/**
 * This sample code demonstrates how a character string can be converted to
 * a TBCD (Telephony Binary Coded Decimal) string and vice versa.
 */

public class TBCDUtil {

    private static String cTBCDSymbolString = "0123456789*#abc";
    private static char[] cTBCDSymbols = cTBCDSymbolString.toCharArray();
    private static Integer mcc = null, mnc = null, lac = null, ci = null, sac = null, uci = null, rac = null, tac = null, enbid = null;
    private static Long eci = null;

    public TBCDUtil() {
    }

    /*public static void main(String[] args) {

        //if (args.length == 0)
        //    return;

        String msisdn = "60193303030";

        byte[] tbcd = parseTBCD(msisdn);

        System.out.println("TBCD as octets: " + dumpBytes(tbcd));
        System.out.println("TBCD octets decoded: " + toTBCDString(tbcd));
    }*/

    /*
     * This method converts a TBCD byte array to a character string.
     */
    public static String toTBCDString(byte[] tbcd) {

        int size = (tbcd == null ? 0 : tbcd.length);
        StringBuffer buffer = new StringBuffer(2*size);
        for (int i=0; i<size; ++i) {
            int octet = tbcd[i];
            int n2 = (octet >> 4) & 0xF;
            int n1 = octet & 0xF;

            if (n1 == 15) {
                throw new NumberFormatException("Illegal filler in octet n=" + i);
            }
            buffer.append(cTBCDSymbols[n1]);

            if (n2 == 15) {
                if (i != size-1)
                    throw new NumberFormatException("Illegal filler in octet n=" + i);
            } else
                buffer.append(cTBCDSymbols[n2]);
        }

        return buffer.toString();
    }

    /*
     * This method converts a character string to a TBCD string.
     */
    public static byte[] parseTBCD(String tbcd) {
        int length = (tbcd == null ? 0:tbcd.length());
        int size = (length + 1)/2;
        byte[] buffer = new byte[size];

        for (int i=0, i1=0, i2=1; i<size; ++i, i1+=2, i2+=2) {

            char c = tbcd.charAt(i1);
            int n2 = getTBCDNibble(c, i1);
            int octet = 0;
            int n1 = 15;
            if (i2 < length) {
                c = tbcd.charAt(i2);
                n1 = getTBCDNibble(c, i2);
            }
            octet = (n1 << 4) + n2;
            buffer[i] = (byte)(octet & 0xFF);
        }

        return buffer;
    }

    private static int getTBCDNibble(char c, int i1) {

        int n = Character.digit(c, 10);

        if (n < 0 || n > 9) {
            switch (c) {
                case '*':
                    n = 10;
                    break;
                case '#':
                    n = 11;
                    break;
                case 'a':
                    n = 12;
                    break;
                case 'b':
                    n = 13;
                    break;
                case 'c':
                    n = 14;
                    break;
                default:
                    throw new NumberFormatException("Bad character '" + c
                        + "' at position " + i1);
            }
        }
        return n;
    }
    /* Hex chars */
    private static final byte[] HEX_CHAR = new byte[]
        { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    public static void setAreaIdParameters(String[] areaId, String areaType) {
        for (int i=0; i < areaId.length; i++) {
            if (i==0)
                mcc = Integer.valueOf(areaId[i]);
            if (i==1)
                mnc = Integer.valueOf(areaId[i]);
            if (i==2) {
                if (areaType.equalsIgnoreCase("locationAreaId") || areaType.equalsIgnoreCase("cellGlobalId") ||
                    areaType.equalsIgnoreCase("routingAreaId"))
                    lac = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("trackingAreaId"))
                    tac = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("utranCellId"))
                    uci = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("eUtranCellId")) {
                    enbid = Integer.valueOf(areaId[i]);
                    eci = Long.valueOf(areaId[i]);
                }
            }
            if (i==3) {
                if (areaType.equalsIgnoreCase("cellGlobalId") || areaType.equalsIgnoreCase("routingAreaId"))
                    ci = sac = rac = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("eUtranCellId"))
                    ci = Integer.valueOf(areaId[i]);
            }
        }
    }

    public static Integer[] setAreaIdParams(String[] areaId, String areaType) {
        Integer[] areaIdParams = new Integer[4];
        for (int i=0; i < areaId.length; i++) {
            if (i==0) {
                mcc = areaIdParams[0] = Integer.valueOf(areaId[i]);
                if (areaType.equalsIgnoreCase("countryCode"))
                    areaIdParams[1] = areaIdParams[2] = areaIdParams[3] = -1;
            }
            if (i==1) {
                mnc = areaIdParams[1] = Integer.valueOf(areaId[i]);
                if (areaType.equalsIgnoreCase("plmnId"))
                    areaIdParams[2] = areaIdParams[3] = -1;
            }
            if (i==2) {
                if (areaType.equalsIgnoreCase("locationAreaId") || areaType.equalsIgnoreCase("cellGlobalId") ||
                    areaType.equalsIgnoreCase("routingAreaId"))
                    lac = areaIdParams[2] = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("utranCellId"))
                    uci = areaIdParams[3] = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("eUtranCellId")) {
                    enbid = areaIdParams[2] = Integer.valueOf(areaId[i]);
                    eci = (long) enbid;
                }
                else if (areaType.equalsIgnoreCase("trackingAreaId")) {
                    tac = areaIdParams[2] = Integer.valueOf(areaId[i]);
                    areaIdParams[3] = -1;
                }
            }
            if (i==3) {
                if (areaType.equalsIgnoreCase("cellGlobalId") || areaType.equalsIgnoreCase("routingAreaId"))
                    ci = sac = rac = areaIdParams[3] = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("eUtranCellId"))
                    ci = areaIdParams[3] = Integer.valueOf(areaId[i]);
            }
        }
        return areaIdParams;
    }

    public static String setAreaIdTbcd(String[] areaId, String areaType) {
        Integer mcc = null, mnc = null, lac = null, ci = null, sac = null, uci = null, rac = null, tac = null, enbid = null;
        Long eci = null;
        String areaIdTbcd = "Invalid";
        for (int i=0; i < areaId.length; i++) {
            if (i==0)
                mcc = Integer.valueOf(areaId[i]);
            if (i==1)
                mnc = Integer.valueOf(areaId[i]);
            if (i==2) {
                if (areaType.equalsIgnoreCase("locationAreaId") || areaType.equalsIgnoreCase("cellGlobalId") ||
                    areaType.equalsIgnoreCase("routingAreaId"))
                    lac = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("trackingAreaId"))
                    tac = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("utranCellId"))
                    uci = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("eUtranCellId")) {
                    enbid = Integer.valueOf(areaId[i]);
                    eci = Long.valueOf(areaId[i]);
                }
            }
            if (i==3) {
                if (areaType.equalsIgnoreCase("cellGlobalId") || areaType.equalsIgnoreCase("routingAreaId"))
                    ci = sac = rac = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("eUtranCellId"))
                    ci = Integer.valueOf(areaId[i]);
            }
        }
        if (areaType.equalsIgnoreCase("cellGlobalId")) {
            CellGlobalIdOrServiceAreaIdFixedLengthImpl cgi = new CellGlobalIdOrServiceAreaIdFixedLengthImpl();
            try {
                cgi.setData(mcc, mnc, lac, ci);
                areaIdTbcd = bytesToHex(cgi.getData());
                return areaIdTbcd;
            } catch (Exception e) {
                return areaIdTbcd;
            }
        } else if (areaType.equalsIgnoreCase("trackingAreaId")) {
            TAIdImpl tai = new TAIdImpl();
            try {
                tai.setData(mcc, mnc, tac);
                areaIdTbcd = bytesToHex(tai.getData());
                return areaIdTbcd;
            } catch (Exception e) {
                return areaIdTbcd;
            }
        } else if (areaType.equalsIgnoreCase("eUtranCellId")) {
            EUtranCgiImpl ecgi = new EUtranCgiImpl();
            try {
                if (ci != null)
                    ecgi.setData(mcc, mnc, enbid, ci);
                else
                    ecgi.setData(mcc, mnc, eci);
                areaIdTbcd = bytesToHex(ecgi.getData());
                return areaIdTbcd;
            } catch (Exception e) {
                return areaIdTbcd;
            }
        }
        return areaIdTbcd;
    }

    public static byte[] setAreaIdtoTbcd(String[] areaId, String areaType) {
        Integer mcc = null, mnc = null, lac = null, ci = null, sac = null, uci = null, rac = null, tac = null, enbid = null;
        Long eci = null;
        byte[] areaIdTbcd = null;
        for (int i=0; i < areaId.length; i++) {
            if (i==0)
                mcc = Integer.valueOf(areaId[i]);
            if (i==1)
                mnc = Integer.valueOf(areaId[i]);
            if (i==2) {
                if (areaType.equalsIgnoreCase("locationAreaId") || areaType.equalsIgnoreCase("cellGlobalId") ||
                    areaType.equalsIgnoreCase("routingAreaId"))
                    lac = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("trackingAreaId"))
                    tac = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("utranCellId"))
                    uci = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("eUtranCellId")) {
                    enbid = Integer.valueOf(areaId[i]);
                    eci = Long.valueOf(areaId[i]);
                }
            }
            if (i==3) {
                if (areaType.equalsIgnoreCase("cellGlobalId") || areaType.equalsIgnoreCase("routingAreaId"))
                    ci = sac = rac = Integer.valueOf(areaId[i]);
                else if (areaType.equalsIgnoreCase("eUtranCellId"))
                    ci = Integer.valueOf(areaId[i]);
            }
        }
        if (areaType.equalsIgnoreCase("cellGlobalId")) {
            CellGlobalIdOrServiceAreaIdFixedLengthImpl cgi = new CellGlobalIdOrServiceAreaIdFixedLengthImpl();
            try {
                cgi.setData(mcc, mnc, lac, ci);
                areaIdTbcd = cgi.getData();
                return areaIdTbcd;
            } catch (MAPException e) {
                return null;
            }
        } else if (areaType.equalsIgnoreCase("trackingAreaId")) {
            TAIdImpl tai = new TAIdImpl();
            try {
                tai.setData(mcc, mnc, tac);
                areaIdTbcd = tai.getData();
                return areaIdTbcd;
            } catch (MAPException e) {
                return null;
            }
        } else if (areaType.equalsIgnoreCase("eUtranCellId")) {
            EUtranCgiImpl ecgi = new EUtranCgiImpl();
            try {
                if (ci != null)
                    ecgi.setData(mcc, mnc, enbid, ci);
                else
                    ecgi.setData(mcc, mnc, eci);
                areaIdTbcd = ecgi.getData();
                return areaIdTbcd;
            } catch (MAPException e) {
                return null;
            }
        }
        return areaIdTbcd;
    }
}

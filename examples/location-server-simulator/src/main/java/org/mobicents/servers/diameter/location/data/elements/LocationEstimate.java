package org.mobicents.servers.diameter.location.data.elements;

/**
 * @author <a href="mailto:aferreiraguido@gmail.com"> Alejandro Ferreira Guido </a>
 * @author <a href="mailto:fernando.mendioroz@gmail.com"> Fernando Mendioroz </a>
 */
public class LocationEstimate {

    public Integer typeOfShape;
    public Double latitude;
    public Double longitude;
    public Double uncertainty;
    public Integer confidence;
    public Double uncertaintySemiMajorAxis;
    public Double uncertaintySemiMinorAxis;
    public Double angleOfMajorAxis;
    public Integer altitude;
    public Double uncertaintyAltitude;
    public Integer innerRadius;
    public Double uncertaintyInnerRadius;
    public Double offsetAngle;
    public Double includedAngle;

}

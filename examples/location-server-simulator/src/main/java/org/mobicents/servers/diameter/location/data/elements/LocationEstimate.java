package org.mobicents.servers.diameter.location.data.elements;

/**
 * @author <a href="mailto:aferreiraguido@gmail.com"> Alejandro Ferreira Guido </a>
 * @author <a href="mailto:fernando.mendioroz@gmail.com"> Fernando Mendioroz </a>
 */
public class LocationEstimate {

    public Integer typeOfShape = 0;

    public Double latitude = 0.00;
    public Double longitude = 0.00;
    public Double uncertainty = 0.00;
    public Integer confidence = 0;
    public Double uncertaintySemiMajorAxis = 0.00;
    public Double uncertaintySemiMinorAxis = 0.00;
    public Double angleOfMajorAxis = 0.00;
    public Integer altitude = 0;
    public Double uncertaintyAltitude = 0.00;
    public Integer innerRadius = 0;
    public Integer uncertaintyInnerRadius = 0;
    public Double offsetAngle = 0.00;
    public Double includedAngle = 0.00;
}

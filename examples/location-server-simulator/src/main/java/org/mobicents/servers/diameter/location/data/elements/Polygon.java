package org.mobicents.servers.diameter.location.data.elements;

import java.io.Serializable;

/**
 * @author <a href="mailto:fernando.mendioroz@gmail.com"> Fernando Mendioroz </a>
 * @author <a href="mailto:aferreiraguido@gmail.com"> Alejandro Ferreira Guido </a>
 */
public interface Polygon extends Serializable {

    byte[] getData();

    int getNumberOfPoints();

    EllipsoidPoint getEllipsoidPoint(int position);

}

package org.mobicents.servers.diameter.gmlc.queue.data;

import java.util.UUID;

public class LocationInformation {

    public enum LocationPrimitive {
        RoutingInformationRequest, ProvideLocationRequest, ProvideLocationAnswer, LocationReportRequest,
        RoutingInformationError, LocationAnswerError
    }

    String sessionId;

    LocationPrimitive primitive;

    LocationInformationRequest requestParameters;
    LocationInformationResponse responseParameters;
    LocationInformationReport reportParameters;

    public LocationPrimitive getPrimitive() {
        return primitive;
    }

    public LocationInformationRequest getRequestParameters() {
        return requestParameters;
    }

    public LocationInformationReport getReportParameters() {
        return reportParameters;
    }

    public LocationInformation(LocationPrimitive primitive) {
        this.primitive = primitive;
        if (primitive.equals(LocationPrimitive.LocationReportRequest))
            this.reportParameters = new LocationInformationReport();
        else
            this.requestParameters = new LocationInformationRequest();
    }

    public String generateSessionId() {
        if (sessionId == null) {
            this.sessionId = UUID.randomUUID().toString();
        }

        return sessionId;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public String toString() {
        return "primitive: " + primitive;
    }

    public void setRoutingInformationError() {
        this.primitive = LocationPrimitive.RoutingInformationError;
    }

    public LocationInformationResponse createResponse(LocationPrimitive primitive) {
        if (responseParameters == null)
            responseParameters = new LocationInformationResponse();

        this.primitive = primitive;

        return responseParameters;
    }

}
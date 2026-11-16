package com.redhat.coolstore.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/services")
public class RestApplication extends Application {
    // Quarkus RESTEasy Reactive auto-registers all @Path resources;
    // this class sets the base application path.
}

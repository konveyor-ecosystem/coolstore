package com.redhat.coolstore.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * This class is not needed in Quarkus. Quarkus automatically scans for JAX-RS resources.
 * However, we will keep it for now to maintain the original functionality.
 */
@ApplicationPath("/services")
public class RestApplication extends Application {

}
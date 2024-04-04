package com.redhat.coolstore.rest;

import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ApplicationPath;

//import io.quarkus.arc.processor.BeanArchive;

//@BeanArchive(name = "services")
@ApplicationPath("/services")
public class RestApplication extends Application {

}

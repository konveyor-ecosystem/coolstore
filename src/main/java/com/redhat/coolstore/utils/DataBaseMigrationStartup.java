package com.redhat.coolstore.utils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@Startup
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @Resource(mappedName = "java:jboss/datasources/CoolstoreDS")
    DataSource dataSource;

    @PostConstruct
    private void startup() {


        try {
            logger.info("Initializing/migrating the database using quarkus-hibernate-orm");
            // TODO: Replace Flyway with quarkus-hibernate-orm
        } catch (Exception e) {
            if(logger !=null)
                logger.log(Level.SEVERE,"FAILED TO INITIALIZE THE DATABASE: " + e.getMessage(),e);
            else
                System.out.println("FAILED TO INITIALIZE THE DATABASE: " + e.getMessage() + " and injection of logger doesn't work");

        }
    }



}

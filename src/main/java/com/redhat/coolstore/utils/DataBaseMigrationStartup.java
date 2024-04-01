package com.redhat.coolstore.utils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import io.deploymen.microdf.MicroDFDataSource;
import java.util.logging.Level;
import java.util.logging.Logger;

@TransactionManagement(TransactionManagementType.BEAN)
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @Resource(mappedName = "CoolstoreDS")
    MicroDFDataSource dataSource;

    @PostConstruct
    private void startup() {


        try {
            logger.info("Initializing/migrating the database using FlyWay");
            Flyway flyway = new Flyway();
            flyway.setDataSource(dataSource);
            flyway.baseline();
            // Start the db.migration
            flyway.migrate();
        } catch (FlywayException e) {
            if(logger !=null)
                logger.log(Level.SEVERE,"FAILED TO INITIALIZE THE DATABASE: " + e.getMessage(),e);
            else
                System.out.println("FAILED TO INITIALIZE THE DATABASE: " + e.getMessage() + " and injection of logger doesn't work");

        }
    }



}

package com.redhat.coolstore.utils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.quarkus.hibernate.orm.DataSource;
import io.quarkus.hibernate.orm.panache.runtime.FlywayOperations;

@ApplicationScoped
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @Inject
    DataSource dataSource;

    @Inject
    FlywayOperations flywayOperations;

    @PostConstruct
    private void startup() {

        try {
            logger.info("Initializing/migrating the database using FlyWay");
            flywayOperations.migrate();
        } catch (Exception e) {
            if(logger !=null)
                logger.log(Level.SEVERE,"FAILED TO INITIALIZE THE DATABASE: " + e.getMessage(),e);
            else
                System.out.println("FAILED TO INITIALIZE THE DATABASE: " + e.getMessage() + " and injection of logger doesn't work");
        }
    }

}

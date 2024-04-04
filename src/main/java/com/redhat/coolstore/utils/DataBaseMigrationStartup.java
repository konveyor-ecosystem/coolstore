package com.redhat.coolstore.utils;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;


@Singleton
@Startup
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @Inject
    DataSource dataSource;

    @PostConstruct
    private void startup() {


        try {
            logger.info("Initializing/migrating the database using FlyWay");
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .baselineOnMigrate(true)
                    .load();
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

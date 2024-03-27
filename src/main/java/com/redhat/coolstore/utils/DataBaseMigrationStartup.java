package com.redhat.coolstore.utils;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;

import javax.enterprise.context.ApplicationScoped;
import javax.sql.DataSource;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by tqvarnst on 2017-04-04.
 */
@ApplicationScoped
@Startup
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @Resource(lookup = "java:/jboss/datasources/CoolstoreDS")
    DataSource dataSource;

    @PostConstruct
    private void onStartUp() {

        try {
            logger.info("Initializing/migrating the database using FlyWay");
            Flyway flyway = Flyway.configure().dataSource(dataSource).load();
            flyway.baseline();
            // Start the db.migration
            flyway.migrate();
        } catch (FlywayException e) {
            if (logger != null)
                logger.log(Level.SEVERE, "FAILED TO INITIALIZE THE DATABASE: " + e.getMessage(), e);
            else
                System.out.println("FAILED TO INITIALIZE THE DATABASE: " + e.getMessage() + " and injection of logger doesn't work");
        }
    }
}
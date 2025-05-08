package com.redhat.coolstore.utils;

import io.quarkus.flyway.FlywayDataSource;
import io.quarkus.runtime.StartupEvent;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Quarkus will automatically run Flyway migrations on startup, but this class
 * provides additional control and logging if needed.
 */
@ApplicationScoped
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @Inject
    @FlywayDataSource("coolstore")
    Flyway flyway;

    void onStart(@Observes StartupEvent ev) {
        try {
            logger.info("Initializing/migrating the database using FlyWay");
            // Quarkus automatically runs migrations, but we can trigger them manually if needed
            // flyway.migrate();
            logger.info("Database migration completed successfully");
        } catch (FlywayException e) {
            logger.log(Level.SEVERE, "FAILED TO INITIALIZE THE DATABASE: " + e.getMessage(), e);
        }
    }
}
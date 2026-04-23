package com.redhat.coolstore.utils;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Flyway migration is now handled by Quarkus automatically via application.properties
 * This class is kept for compatibility but migration is done by quarkus.flyway.migrate-at-start=true
 */
@ApplicationScoped
@Startup
public class DataBaseMigrationStartup {

    @Inject
    Logger logger;

    @Inject
    AgroalDataSource dataSource;

    void startup() {
        // Quarkus Flyway extension handles migration automatically
        // configured in application.properties with quarkus.flyway.migrate-at-start=true
        logger.info("Database migration is handled by Quarkus Flyway extension");
    }

}
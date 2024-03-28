package com.redhat.coolstore.utils;

import io.deploy.micrometer.api.MicrometerRegistry;
import io.quarkus.hibernate.transaction.Transactional;
import io.quarkus.arc.runtime.BeanCreator;
import io.quarkus.hibernate.orm.runtime.JdbcDataSource;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

@Startup
@BeanCreator
public class DataBaseMigrationStartup {

    @Inject
    Log logger;

    @Inject
    JdbcDataSource dataSource;

    @PostConstruct
    @Transactional
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

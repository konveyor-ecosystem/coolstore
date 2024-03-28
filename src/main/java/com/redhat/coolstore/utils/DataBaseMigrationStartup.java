package com.redhat.coolstore.utils;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.inject.Inject;
import io.quarkus.flyway.QuarkusFlyway;
import io.quarkus.log.Log;
import jakarta.inject.Singleton;
import jakarta.sql.DataSource;
import org.flywaydb.core.FlywayException;

@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class DataBaseMigrationStartup {

    @Inject
    Log log;

    @Inject
    DataSource dataSource;

    @PostConstruct
    public void startup() {
        QuarkusFlyway flyway = new QuarkusFlyway();
        flyway.setDataSource(dataSource);
        flyway.baseline();
        flyway.migrate()
            .ifException(throwable -> log.severe("FAILED TO INITIALIZE THE DATABASE: " + throwable.getMessage(), throwable));
    }

}

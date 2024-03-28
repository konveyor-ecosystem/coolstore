package com.redhat.coolstore.utils;

import io.quarkus.flyway.QuarkusFlyway;
import io.quarkus.log.Log;
import io.quarkus.transaction.Transactional;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.sql.DataSource;
import org.flywaydb.core.api.FlywayException;

@ApplicationScoped
@Transactional
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

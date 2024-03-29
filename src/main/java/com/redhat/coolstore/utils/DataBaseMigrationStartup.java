package com.redhat.coolstore.utils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.inject.Inject;
import org.hibernate.orm.jdbc.java8.Bindable;
import io.quarkus.logging.Log;

@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class DataBaseMigrationStartup {

    @Inject
    Log logger;

    @Resource(mappedName = "java:jboss/datasources/CoolstoreDS")
    Bindable<DataSource> dataSource;

    @PostConstruct
    private void startup() {


        try {
            logger.info("Initializing/migrating the database using Hibernate ORM");
            // Start the db.migration
            dataSource.get().migrate();
        } catch (Exception e) {
            if(logger !=null)
                logger.error("FAILED TO INITIALIZE THE DATABASE: " + e.getMessage(),e);
            else
                System.out.println("FAILED TO INITIALIZE THE DATABASE: " + e.getMessage() + " and injection of logger doesn't work");

        }
    }



}

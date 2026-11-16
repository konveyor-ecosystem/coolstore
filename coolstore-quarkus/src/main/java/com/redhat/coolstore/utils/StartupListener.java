package com.redhat.coolstore.utils;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StartupListener {

    private static final Logger LOG = Logger.getLogger(StartupListener.class);

    void onStart(@Observes StartupEvent ev) {
        LOG.info("AppListener(postStart) - CoolStore Quarkus application starting up");
    }

    void onStop(@Observes ShutdownEvent ev) {
        LOG.info("AppListener(preStop) - CoolStore Quarkus application shutting down");
    }
}

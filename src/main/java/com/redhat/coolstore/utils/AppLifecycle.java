package com.redhat.coolstore.utils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.jboss.logging.Logger;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

/**
 * Replaces the WebLogic StartupListener (ApplicationLifecycleListener).
 * Observes Quarkus CDI startup/shutdown events instead.
 */
@ApplicationScoped
public class AppLifecycle {

    private static final Logger LOG = Logger.getLogger(AppLifecycle.class);

    void onStart(@Observes StartupEvent ev) {
        LOG.info("AppListener(postStart)");
    }

    void onStop(@Observes ShutdownEvent ev) {
        LOG.info("AppListener(preStop)");
    }
}

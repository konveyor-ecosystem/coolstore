package com.redhat.coolstore.utils;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

/**
 * Replaces the legacy legacy {@code StartupListener} application lifecycle
 * listener with Quarkus CDI lifecycle event observers.
 */
@ApplicationScoped
public class AppLifecycle {

    private static final Logger log = Logger.getLogger(AppLifecycle.class);

    void onStart(@Observes StartupEvent ev) {
        log.info("AppListener(postStart)");
    }

    void onStop(@Observes ShutdownEvent ev) {
        log.info("AppListener(preStop)");
    }

}

package com.redhat.coolstore.utils;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

/** Replaces WebLogic StartupListener [D10]. */
@ApplicationScoped
public class Lifecycle {
    private static final Logger LOG = Logger.getLogger(Lifecycle.class);
    void onStart(@Observes StartupEvent ev) { LOG.info("CoolStore started"); }
    void onStop(@Observes ShutdownEvent ev) { LOG.info("CoolStore stopping"); }
}

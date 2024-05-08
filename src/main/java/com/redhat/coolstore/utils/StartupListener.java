package com.redhat.coolstore.utils;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StartupListener {

    @Inject
    Logger log;

    void onStart(@Observes @Initialized(ApplicationScoped.class) Object init) {
        log.info("AppListener(postStart)");
    }

    void onStop(@Observes @Destroyed(ApplicationScoped.class) Object init) {
        log.info("AppListener(preStop)");
    }

}

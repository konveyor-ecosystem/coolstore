package com.redhat.coolstore.utils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StartupListener {

    @Inject
    Logger log;

    public void onStartup() {
        log.info("AppListener(onStartup)");
    }

    public void onShutdown() {
        log.info("AppListener(onShutdown)");
    }

}

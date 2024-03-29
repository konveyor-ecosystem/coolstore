package com.redhat.coolstore.utils;

import jakarta.application.ApplicationScoped;
import jakarta.application.ApplicationListener;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class StartupListener implements ApplicationListener {

    @Inject
    Logger log;

    @Override
    public void onStartup(StartupEvent event) {
        log.info("AppListener(onStartup)");
    }

    @Override
    public void onShutdown(ShutdownEvent event) {
        log.info("AppListener(onShutdown)");
    }

}

package com.redhat.coolstore.utils;

import jakarta.inject.Inject; // Replaced javax.inject with jakarta.inject
import java.util.logging.Logger;
import jakarta.annotation.PostConstruct; // Added import for jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy; // Added import for jakarta.annotation.PreDestroy

public class StartupListener extends ApplicationLifecycleListener {

    @Inject
    Logger log;

    @Override
    public void postStart(ApplicationLifecycleEvent evt) {
        log.info("AppListener(postStart)");
    }

    @Override
    public void preStop(ApplicationLifecycleEvent evt) {
        log.info("AppListener(preStop)");
    }

}
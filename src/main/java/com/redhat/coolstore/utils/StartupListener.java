package com.redhat.coolstore.utils;

import weblogic.application.ApplicationLifecycleEvent;
import weblogic.application.ApplicationLifecycleListener;

import jakarta.inject.Inject;
import java.util.logging.Logger;
import jakarta.inject.Singleton;

@Singleton
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

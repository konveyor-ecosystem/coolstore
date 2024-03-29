package com.redhat.coolstore.utils;

import jakarta.inject.Inject;
import org.jboss.logging.Logger;

public class StartupListener implements org.eclipse.microprofile.config.spi.ConfigSource {

    @Inject
    Logger log;

    @Override
    public void postStart(org.eclipse.microprofile.config.spi.ConfigSource.Event evt) {
        log.info("AppListener(postStart)");
    }

    @Override
    public void preStop(org.eclipse.microprofile.config.spi.ConfigSource.Event evt) {
        log.info("AppListener(preStop)");
    }

}

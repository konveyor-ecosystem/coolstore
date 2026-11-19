package com.redhat.coolstore.utils;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.enterprise.audit.logging.config.AuditConfiguration;
import com.enterprise.audit.logging.service.AuditLogger;
import com.enterprise.audit.logging.service.StreamableAuditLogger;

/**
 * Produces the audit-logging-library 2.0.0 {@link StreamableAuditLogger} (TCP streaming),
 * replacing the legacy FileSystemAuditLogger that wrote to ./device-inventory-audit-logs.
 * The TCP endpoint is externalized via configuration (Secret on OpenShift).
 */
@ApplicationScoped
public class AuditLoggerProducer {

    @ConfigProperty(name = "audit.stream.host", defaultValue = "localhost")
    String streamHost;

    @ConfigProperty(name = "audit.stream.port", defaultValue = "4560")
    int streamPort;

    @ConfigProperty(name = "audit.stream.protocol", defaultValue = "tcp")
    String streamProtocol;

    private StreamableAuditLogger logger;

    @Produces
    public AuditLogger produceAuditLogger() {
        if (logger == null) {
            AuditConfiguration config = new AuditConfiguration();
            config.setStreamHost(streamHost);
            config.setStreamPort(streamPort);
            config.setStreamProtocol(streamProtocol);
            logger = new StreamableAuditLogger(config);
        }
        return logger;
    }

    @PreDestroy
    public void close() {
        if (logger != null) {
            logger.close();
        }
    }
}

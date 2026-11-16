package com.redhat.coolstore.utils;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * AuditService replaces the proprietary com.enterprise:audit-logging-library FileSystemAuditLogger.
 * Writes structured audit records to the configured log directory and also emits structured
 * JSON log lines for OpenShift log aggregation.
 */
@ApplicationScoped
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class);
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @ConfigProperty(name = "audit.log.directory", defaultValue = "./device-inventory-audit-logs")
    String logDirectory;

    private Path logDir;
    private Path logFile;

    @PostConstruct
    void init() {
        logDir = Paths.get(logDirectory);
        try {
            Files.createDirectories(logDir);
            logFile = logDir.resolve("audit.log");
            LOG.infof("AuditService initialized. Writing audit logs to: %s", logFile.toAbsolutePath());
        } catch (IOException e) {
            LOG.errorf(e, "Failed to create audit log directory: %s", logDirectory);
        }
    }

    /**
     * Log an audit event. Writes to filesystem and to structured log.
     *
     * @param eventType  The type of event (e.g., "ORDER_PLACED", "INVENTORY_UPDATED")
     * @param entityId   The ID of the entity being acted upon
     * @param details    Additional details about the event
     */
    public void logEvent(String eventType, String entityId, String details) {
        String timestamp = TIMESTAMP_FMT.format(Instant.now());
        String record = String.format("[%s] EVENT=%s ENTITY_ID=%s DETAILS=%s%n",
                timestamp, eventType, entityId, details);

        // Write to filesystem (preserving original FileSystemAuditLogger behaviour)
        if (logFile != null) {
            try {
                Files.writeString(logFile, record,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOG.errorf(e, "Failed to write audit log entry: %s", record);
            }
        }

        // Also emit structured log line for OpenShift log aggregation
        LOG.infof("{\"audit\":true,\"timestamp\":\"%s\",\"eventType\":\"%s\",\"entityId\":\"%s\",\"details\":\"%s\"}",
                timestamp, eventType, entityId, details);
    }

    /**
     * Log an order audit event.
     */
    public void logOrderEvent(String orderId, String action, String details) {
        logEvent("ORDER_" + action.toUpperCase(), orderId, details);
    }
}

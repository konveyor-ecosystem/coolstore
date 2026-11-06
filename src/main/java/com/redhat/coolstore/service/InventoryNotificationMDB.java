package com.redhat.coolstore.service;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import io.quarkus.runtime.StartupEvent;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import org.jboss.logging.Logger;

/**
 * Monitors the orders topic and warns when item inventory falls below the
 * low-stock threshold.
 *
 * <p>Note: the legacy legacy raw JMS client was never initialized at runtime
 * (its {@code init()} was never invoked, leaving it inert). The Quarkus
 * version actively subscribes to the topic on startup, per migration plan
 * task 4.5.</p>
 */
@ApplicationScoped
public class InventoryNotificationMDB implements MessageListener {

    private static final int LOW_THRESHOLD = 50;

    private static final Logger log = Logger.getLogger(InventoryNotificationMDB.class);

    @Inject
    private CatalogService catalogService;

    @Inject
    ConnectionFactory connectionFactory;

    @ConfigProperty(name = "orders.consumers.enabled", defaultValue = "true")
    boolean consumersEnabled;

    @ConfigProperty(name = "orders.topic.name", defaultValue = "topic/orders")
    String ordersTopic;

    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    void onStart(@Observes StartupEvent ev) {
        if (!consumersEnabled) {
            return;
        }
        try {
            connection = connectionFactory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            consumer = session.createConsumer(session.createTopic(ordersTopic));
            consumer.setMessageListener(this);
            connection.start();
            log.info("Inventory notification subscriber started on " + ordersTopic);
        } catch (JMSException e) {
            log.error("Failed to start inventory notification subscriber on " + ordersTopic, e);
        }
    }

    @PreDestroy
    void onDestroy() {
        try {
            if (consumer != null) {
                consumer.close();
            }
        } catch (JMSException e) {
            log.debug("Failed to close inventory notification consumer", e);
        }
        try {
            if (session != null) {
                session.close();
            }
        } catch (JMSException e) {
            log.debug("Failed to close inventory notification session", e);
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (JMSException e) {
            log.debug("Failed to close inventory notification connection", e);
        }
    }

    @Override
    public void onMessage(Message rcvMessage) {
        TextMessage msg;
        {
            try {
                log.info("received message inventory");
                if (rcvMessage instanceof TextMessage) {
                    msg = (TextMessage) rcvMessage;
                    String orderStr = msg.getBody(String.class);
                    Order order = Transformers.jsonToOrder(orderStr);
                    order.getItemList().forEach(orderItem -> {
                        int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
                        int new_quantity = old_quantity - orderItem.getQuantity();
                        if (new_quantity < LOW_THRESHOLD) {
                            log.warn("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
                        } else {
                            orderItem.setQuantity(new_quantity);
                        }
                    });
                }


            } catch (JMSException jmse) {
                log.error("An exception occurred: " + jmse.getMessage());
            }
        }
    }

}

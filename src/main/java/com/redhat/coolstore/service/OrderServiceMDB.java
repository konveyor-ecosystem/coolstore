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
 * Replaces the former legacy/EJB message-driven bean: a CDI bean that
 * implements {@link MessageListener} and registers itself as a consumer on
 * the orders topic at application startup (Artemis via Quarkus).
 */
@ApplicationScoped
public class OrderServiceMDB implements MessageListener {

	private static final Logger log = Logger.getLogger(OrderServiceMDB.class);

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

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
			log.info("Order consumer started on " + ordersTopic);
		} catch (JMSException e) {
			log.error("Failed to start order consumer on " + ordersTopic, e);
		}
	}

	@PreDestroy
	void onDestroy() {
		try {
			if (consumer != null) {
				consumer.close();
			}
		} catch (JMSException e) {
			log.debug("Failed to close order consumer", e);
		}
		try {
			if (session != null) {
				session.close();
			}
		} catch (JMSException e) {
			log.debug("Failed to close order session", e);
		}
		try {
			if (connection != null) {
				connection.close();
			}
		} catch (JMSException e) {
			log.debug("Failed to close order connection", e);
		}
	}

	@Override
	public void onMessage(Message rcvMessage) {
		log.info("\nMessage recd !");
		TextMessage msg = null;
		try {
				if (rcvMessage instanceof TextMessage) {
						msg = (TextMessage) rcvMessage;
						String orderStr = msg.getBody(String.class);
						log.info("Received order: " + orderStr);
						Order order = Transformers.jsonToOrder(orderStr);
						log.info("Order object is " + order);
						orderService.save(order);
						order.getItemList().forEach(orderItem -> {
							catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
						});
				}
		} catch (JMSException e) {
			throw new RuntimeException(e);
		}
	}

}

package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Topic;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

import org.jboss.logging.Logger;

@ApplicationScoped
public class ShoppingCartOrderProcessor  {

    private static final Logger log = Logger.getLogger(ShoppingCartOrderProcessor.class);

    @Inject
    ConnectionFactory connectionFactory;

    @ConfigProperty(name = "orders.topic.name", defaultValue = "topic/orders")
    String ordersTopic;

    public void  process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        try (JMSContext context = connectionFactory.createContext()) {
            Topic topic = context.createTopic(ordersTopic);
            context.createProducer().send(topic, Transformers.shoppingCartToJson(cart));
        }
    }

}

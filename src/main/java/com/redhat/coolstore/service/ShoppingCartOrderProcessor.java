package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.logging.Logger;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

import io.smallrye.reactive.messaging.MutinyEmitter;

/**
 * Publishes checkout orders as JSON to the "orders" AMQP channel, replacing the
 * injected JMSContext + @Resource(lookup = "java:/topic/orders") Topic producer.
 */
@ApplicationScoped
public class ShoppingCartOrderProcessor {

    @Inject
    Logger log;

    @Inject
    @Channel("orders")
    private MutinyEmitter<String> ordersEmitter;

    public void process(ShoppingCart cart) {
        log.info("Sending order from processor");
        ordersEmitter.sendAndAwait(Transformers.shoppingCartToJson(cart));
    }
}

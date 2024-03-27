package com.redhat.coolstore.service;

import java.util.logging.Logger;
import javax.inject.Inject;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Emitter;
import io.smallrye.reactive.messaging.Message; // Updated import for SmallRye Reactive Messaging Message
import javax.enterprise.context.ApplicationScoped; // Added import for CDI ApplicationScoped

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped // Replaced @Stateless with @ApplicationScoped for CDI bean
public class ShoppingCartOrderProcessor {

    @Inject
    Logger log;

    @Inject
    private transient Message<String> message; // Replaced JMSContext with Message

    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;

    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersEmitter.send(Transformers.shoppingCartToJson(cart));
    }
}
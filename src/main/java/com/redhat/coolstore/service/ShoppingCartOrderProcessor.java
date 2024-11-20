package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.inject.Inject;
import io.smallrye.reactive.messaging.Channel;
import io.smallrye.reactive.messaging.Emitter;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    @Channel("ordersTopic")
    Emitter<String> ordersTopicEmitter;

    public void  process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersTopicEmitter.send(Transformers.shoppingCartToJson(cart));
    }
}
package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.inject.Inject;

import io.smallrye.reactive.messaging.Emitter;
import io.smallrye.reactive.messaging.annotations.Channel;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    @Channel("orders") // Updated to use Emitter feeding a Channel
    Emitter<String> ordersTopicEmitter;

    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersTopicEmitter.send(Transformers.shoppingCartToJson(cart));
    }

}
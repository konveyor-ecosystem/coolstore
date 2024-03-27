package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import jakarta.ejb.ApplicationScoped;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    @Channel("orders")
    Emitter<String> ordersTopicEmitter;

    public void  process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersTopicEmitter.send(Transformers.shoppingCartToJson(cart));
    }
}
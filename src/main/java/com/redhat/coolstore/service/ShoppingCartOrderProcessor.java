package com.redhat.coolstore.service;

import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class ShoppingCartOrderProcessor {

    private static final Logger LOG = Logger.getLogger(ShoppingCartOrderProcessor.class.getName());

    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;

    @Inject
    @Channel("inventory")
    Emitter<String> inventoryEmitter;

    public void process(ShoppingCart cart) {
        LOG.info("Sending order from processor: ");
        String json = Transformers.shoppingCartToJson(cart);
        ordersEmitter.send(json);
        inventoryEmitter.send(json);
    }
}

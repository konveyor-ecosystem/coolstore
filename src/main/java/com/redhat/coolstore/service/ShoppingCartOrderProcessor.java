package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.annotation.Inject;
import io.smallrye.mutiny.Multi;
import io.smallrye.reactive.messaging.Channel;
import io.smallrye.reactive.messaging.Emitter;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@jakarta.enterprise.context.ApplicationScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;

    public void  process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersEmitter.send(Transformers.shoppingCartToJson(cart));
    }
}
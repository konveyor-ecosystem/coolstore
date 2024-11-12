
package com.redhat.coolstore.service;

import java.util.logging.Logger;
import javax.inject.Inject;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

import javax.enterprise.context.ApplicationScoped;
import io.smallrye.reactive.messaging.Mediator;
import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Emitter;

@ApplicationScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;

    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersEmitter.send(Transformers.shoppingCartToJson(cart));
    }
}

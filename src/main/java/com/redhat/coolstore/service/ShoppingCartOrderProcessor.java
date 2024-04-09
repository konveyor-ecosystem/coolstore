package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.annotation.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.Emitter;
import jakarta.jms.JsonbMessage;
import org.eclipse.microprofile.reactive.messaging.Channel;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;


    @Inject
    @Channel("orders")
    private Emitter<JsonbMessage> ordersEmitter;

    public void  process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersEmitter.send(Transformers.shoppingCartToJsonMessage(cart));
    }



}

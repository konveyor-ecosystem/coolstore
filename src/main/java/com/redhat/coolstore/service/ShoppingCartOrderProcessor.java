
package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.ejb.Stateless;
import jakarta.annotation.Resource; // Replaced javax.annotation with jakarta.annotation
import javax.inject.Inject;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@Stateless
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    @Channel("orders") // Added @Channel annotation
    Emitter<String> ordersTopicEmitter; // Replaced Topic with Emitter

    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersTopicEmitter.send(Transformers.shoppingCartToJson(cart));
    }

}

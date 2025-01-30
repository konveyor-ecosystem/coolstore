package com.redhat.coolstore.service;

import io.quarkus.arc.Singleton;
import io.smallrye.reactive.messaging.Channel;
import io.smallrye.reactive.messaging.Emitter;
import javax.inject.Inject;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@Singleton
public class ShoppingCartOrderProcessor  {

    @Inject
    @Channel("orders-topic")
    Emitter<String> ordersTopic;

    public void  process(ShoppingCart cart) {
        ordersTopic.send(Transformers.shoppingCartToJson(cart));
    }
}
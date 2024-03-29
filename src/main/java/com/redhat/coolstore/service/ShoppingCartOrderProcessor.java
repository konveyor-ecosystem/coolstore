package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import io.smallrye.mutiny.subscription.PushSubscriber;
import io.smallrye.reactive.messaging.Channel;
import io.smallrye.reactive.messaging.Emitter;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    private JMSContext context;

    @Resource(lookup = "java:/topic/orders")
    @Channel("orders")
    Emitter<String> ordersEmitter;

    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersEmitter.send(Transformers.shoppingCartToJson(cart))
          .subscribe().with(new PushSubscriber<Void>() {
            @Override
            public void onSubscribe(Subscription subscription) {
              // request all items at subscription time
              subscription.request(Integer.MAX_VALUE);
            }

            @Override
            public void onNext(Void item) {
              // no-op
            }

            @Override
            public void onError(Throwable throwable) {
              // handle error
            }

            @Override
            public void onComplete() {
              // no-op
            }
          });
    }

}

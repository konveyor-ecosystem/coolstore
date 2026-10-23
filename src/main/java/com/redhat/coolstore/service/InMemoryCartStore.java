package com.redhat.coolstore.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import com.redhat.coolstore.model.ShoppingCart;

/** Replaces the @Stateful bean + replicated HTTP session (Konveyor session-00000). */
@ApplicationScoped
public class InMemoryCartStore implements CartStore {

    private final Map<String, ShoppingCart> carts = new ConcurrentHashMap<>();

    @Override
    public ShoppingCart getOrCreate(String cartId) {
        return carts.computeIfAbsent(cartId, id -> new ShoppingCart());
    }
}

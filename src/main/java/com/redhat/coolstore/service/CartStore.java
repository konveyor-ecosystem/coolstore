package com.redhat.coolstore.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import com.redhat.coolstore.model.ShoppingCart;

/** Per-pod in-memory cart store replacing the @Stateful/@SessionScoped cart (no replication; use sticky Route). */
@ApplicationScoped
public class CartStore {
    private final Map<String, ShoppingCart> carts = new ConcurrentHashMap<>();

    public ShoppingCart getOrCreate(String cartId) {
        return carts.computeIfAbsent(cartId, id -> new ShoppingCart());
    }

    public void remove(String cartId) {
        carts.remove(cartId);
    }
}

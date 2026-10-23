package com.redhat.coolstore.service;

import com.redhat.coolstore.model.ShoppingCart;

/** Cart storage abstraction. In-memory today; Data Grid/Redis implementation is a documented follow-up. */
public interface CartStore {
    ShoppingCart getOrCreate(String cartId);
}

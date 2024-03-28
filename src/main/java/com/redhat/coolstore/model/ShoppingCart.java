package com.redhat.coolstore.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.enterprise.context.Dependent;
import io.quarkus.runtime.annotations.RegisterForReflection;

@Dependent
@RegisterForReflection
public class ShoppingCart {

    private double cartItemTotal;

    private double cartItemPromoSavings;

    private double shippingTotal;

    private double shippingPromoSavings;

    private double cartTotal;

    private List<ShoppingCartItem> shoppingCartItemList = new ArrayList<ShoppingCartItem>();

    public ShoppingCart() {
    }

    public List<ShoppingCartItem> getShoppingCartItemList() {
        return new ArrayList<>(shoppingCartItemList);
    }

    public void updateShoppingCartItemList(ShoppingCartItem sci) {
        if (sci != null) {
            shoppingCartItemList = new ArrayList<>(shoppingCartItemList);
            shoppingCartItemList.removeIf(item -> Objects.equals(item, sci));
            shoppingCartItemList.add(sci);
        }
    }

    public double getCartItemTotal() {
        return cartItemTotal;
    }

    public void setCartItemTotal(double cartItemTotal) {
        this.cartItemTotal = cartItemTotal;
    }

    public double getShippingTotal() {
        return shippingTotal;
    }

    public void setShippingTotal(double shippingTotal) {
        this.shippingTotal = shippingTotal;
    }

    public double getCartTotal() {
        return cartTotal;
    }

    public void setCartTotal(double cartTotal) {
        this.cartTotal = cartTotal;
    }

    public double getCartItemPromoSavings() {
        return cartItemPromoSavings;
    }

    public void setCartItemPromoSavings(double cartItemPromoSavings) {
        this.cartItemPromoSavings = cartItemPromoSavings;
    }

    public double getShippingPromoSavings() {
        return shippingPromoSavings;
    }

    public void setShippingPromoSavings(double shippingPromoSavings) {
        this.shippingPromoSavings = shippingPromoSavings;
    }

    @Override
    public String toString() {
        return "ShoppingCart [cartItemTotal=" + cartItemTotal
                + ", cartItemPromoSavings=" + cartItemPromoSavings
                + ", shippingTotal=" + shippingTotal
                + ", shippingPromoSavings=" + shippingPromoSavings
                + ", cartTotal=" + cartTotal + "]";
    }
}

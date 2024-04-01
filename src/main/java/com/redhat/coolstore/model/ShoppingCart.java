package com.redhat.coolstore.model;

import io.smallrye.mutiny.Mutiny;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import java.util.stream.Collectors;

public class ShoppingCart {

    private double cartItemTotal;

    private double cartItemPromoSavings;

    private double shippingTotal;

    private double shippingPromoSavings;

    private double cartTotal;

    private Mutiny.Builder builder = Mutiny.builder();
    private Mutiny.Multi<Tuple2<ShoppingCartItem, Integer>> shoppingCartItemList;

    public ShoppingCart() {
    }

    public Mutiny.Multi<Tuple2<ShoppingCartItem, Integer>> getShoppingCartItemList() {
        return shoppingCartItemList;
    }

    public Uni<Void> addShoppingCartItem(ShoppingCartItem sci) {
        return Uni.createFrom().item(() -> {
            shoppingCartItemList.emit(Tuple.of(sci, 1));
            return null;
        });
    }

    public Uni<Boolean> removeShoppingCartItem(ShoppingCartItem sci) {
        return Uni.createFrom().item(() -> {
            boolean removed = shoppingCartItemList.filter(tuple -> tuple.getItem().equals(sci)).onItem().first().invoke(tuple -> {
                shoppingCartItemList.remove(tuple);
                return true;
            });
            return removed;
        });
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
                + ", cartTotal=" + cartTotal + ", shoppingCartItemList="
                + shoppingCartItemList.stream().map(tuple -> tuple.getItem()).collect(Collectors.toList()) + "]";
    }
}

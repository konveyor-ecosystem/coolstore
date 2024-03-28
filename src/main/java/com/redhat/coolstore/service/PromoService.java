package com.redhat.coolstore.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import com.redhat.coolstore.model.Promotion;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

@ApplicationScoped
public class PromoService {

    private Set<Promotion> promotionSet;

    private Map<String, Promotion> promotionMap;

    public PromoService() {

        promotionSet = new HashSet<>();

        promotionSet.add(new Promotion("329299", .25));

        promotionMap = new ConcurrentHashMap<>();

        for (Promotion promo : getPromotions()) {

            promotionMap.put(promo.getItemId(), promo);

        }

    }

    public void applyCartItemPromotions(ShoppingCart shoppingCart) {

        if (shoppingCart != null && shoppingCart.getShoppingCartItemList().size() > 0) {

            for (ShoppingCartItem sci : shoppingCart.getShoppingCartItemList()) {

                String productId = sci.getProduct().getItemId();

                Promotion promo = promotionMap.get(productId);

                if (promo != null) {

                    sci.setPromoSavings(sci.getProduct().getPrice() * promo.getPercentOff() * -1);
                    sci.setPrice(sci.getProduct().getPrice() * (1 - promo.getPercentOff()));

                }

            }

        }

    }

    public void applyShippingPromotions(ShoppingCart shoppingCart) {

        if (shoppingCart != null) {

            //PROMO: if cart total is greater than 75, free shipping
            if (shoppingCart.getCartItemTotal() >= 75) {

                shoppingCart.setShippingPromoSavings(shoppingCart.getShippingTotal() * -1);
                shoppingCart.setShippingTotal(0);

            }

        }

    }

    public Set<Promotion> getPromotions() {

        return promotionSet;

    }

    public void setPromotions(Set<Promotion> promotionSet) {

        this.promotionSet = promotionSet;

    }

    @Override
    public String toString() {
        return "PromoService [promotionSet=" + promotionSet + ", promotionMap=" + promotionMap + "]";
    }

}


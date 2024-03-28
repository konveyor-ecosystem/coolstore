package com.redhat.coolstore.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashSet;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import com.redhat.coolstore.model.Promotion;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

@ApplicationScoped
public class PromoService {

    private ConcurrentHashSet<Promotion> promotionSet = new ConcurrentHashSet<>();

    public void applyCartItemPromotions(ShoppingCart shoppingCart) {

        if (shoppingCart != null && shoppingCart.getShoppingCartItemList().size() > 0) {

            ConcurrentHashMap<String, Promotion> promoMap = new ConcurrentHashMap<>();

            for (Promotion promo : promotionSet) {

                promoMap.put(promo.getItemId(), promo);

            }

            for (ShoppingCartItem sci : shoppingCart.getShoppingCartItemList()) {

                String productId = sci.getProduct().getItemId();

                Promotion promo = promoMap.get(productId);

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

}


package com.redhat.coolstore.service;

import java.util.ImmutableMap;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.runtime.annotations.Override;
import io.smallrye.common.collection.FastSet;
import io.smallrye.common.mapper.runtime.util.ImmutableHashMap;
import com.redhat.coolstore.model.Promotion;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

@ApplicationScoped
public class PromoService {

    private static final long serialVersionUID = 2088590587856645568L;

    private String name = null;

    private FastSet<Promotion> promotionSet = null;

    public PromoService() {

        promotionSet = FastSet.of(new Promotion("329299", .25));

    }

    public void applyCartItemPromotions(ShoppingCart shoppingCart) {

        if (shoppingCart != null && shoppingCart.getShoppingCartItemList().size() > 0) {

            ImmutableMap<String, Promotion> promoMap = promotionSet.stream().collect(ImmutableHashMap.toImmutableMap(Promotion::getItemId));

            shoppingCart.getShoppingCartItemList().forEach(sci -> {
                String productId = sci.getProduct().getItemId();
                Promotion promo = promoMap.get(productId);
                if (promo != null) {
                    sci.setPromoSavings(sci.getProduct().getPrice() * promo.getPercentOff() * -1);
                    sci.setPrice(sci.getProduct().getPrice() * (1 - promo.getPercentOff()));
                }
            });

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

    public FastSet<Promotion> getPromotions() {

        if (promotionSet == null) {

            promotionSet = FastSet.of();

        }

        return promotionSet;

    }

    public void setPromotions(FastSet<Promotion> promotionSet) {

        if (promotionSet != null) {

            this.promotionSet = promotionSet;

        } else {

            this.promotionSet = FastSet.of();

        }

    }

}
package com.redhat.coolstore.service;

import org.jboss.logging.Logger;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

@SessionScoped
public class ShoppingCartService  {

    @Inject
    Logger log;

    @Inject
    ShoppingCartOrderProcessor shoppingCartOrderProcessor;

    private ShoppingCart cart  = new ShoppingCart(); //Each user can have multiple shopping carts (tabbed browsing)

    public ShoppingCartService() {
    }

    public ShoppingCart getShoppingCart(String cartId) {
        return cart;
    }

    public ShoppingCart checkOutShoppingCart(String cartId) {
        ShoppingCart cart = this.getShoppingCart(cartId);
      
        log.info("Sending  order: ");
        shoppingCartOrderProcessor.process(cart);

        cart.resetShoppingCartItemList();
        priceShoppingCart(cart);
        return cart;
    }

    public void priceShoppingCart(ShoppingCart sc) {

        if (sc != null) {

            initShoppingCartForPricing(sc);

            if (sc.getShoppingCartItemList() != null && sc.getShoppingCartItemList().size() > 0) {

                //ps.applyCartItemPromotions(sc);

                for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {

                    sc.setCartItemPromoSavings(
                            sc.getCartItemPromoSavings() + sci.getPromoSavings() * sci.getQuantity());
                    sc.setCartItemTotal(sc.getCartItemTotal() + sci.getPrice() * sci.getQuantity());

                }

                //sc.setShippingTotal(lookupShippingServiceRemote().calculateShipping(sc));
                sc.setShippingTotal(calculateShipping(sc));

                if (sc.getCartItemTotal() >= 25) {
                    sc.setShippingTotal(sc.getShippingTotal()
                            + calculateShippingInsurance(sc));
                }

            }

            //ps.applyShippingPromotions(sc);

            sc.setCartTotal(sc.getCartItemTotal() + sc.getShippingTotal());

        }

    }

    private void initShoppingCartForPricing(ShoppingCart sc) {

        sc.setCartItemTotal(0);
        sc.setCartItemPromoSavings(0);
        sc.setShippingTotal(0);
        sc.setShippingPromoSavings(0);
        sc.setCartTotal(0);

        for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {
            Product p = getProduct(sci.getProduct().getItemId());
            //if product exist
            if (p != null) {
                sci.setProduct(p);
                sci.setPrice(p.getPrice());
            }

            sci.setPromoSavings(0);
        }

    }

    public Product getProduct(String itemId) {
        //return productServices.getProductByItemId(itemId);
        // Implement a new method to get the product by itemId
        return null;
    }

    private double calculateShipping(ShoppingCart sc) {
        // Implement a new method to calculate shipping costs
        return 0;
    }

    private double calculateShippingInsurance(ShoppingCart sc) {
        // Implement a new method to calculate shipping insurance costs
        return 0;
    }
}

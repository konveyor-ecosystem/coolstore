package com.redhat.coolstore.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterStyle;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.headers.Headers;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeFlow;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.OAuthFlow;
import org.eclipse.microprofile.openapi.annotations.security.OAuthFlows;
import org.eclipse.microprofile.openapi.annotations.security.OAuthScope;
import org.eclipse.microprofile.openapi.annotations.security.OAuthScopes;

import org.eclipse.microprofile.openapi.annotations.servers.Server;
import org.eclipse.microprofile.openapi.annotations.servers.Servers;
import org.eclipse.microprofile.openapi.annotations.ServerVariable;
import org.eclipse.microprofile.openapi.annotations.enums.ServerVariableEnum;
import org.eclipse.microprofile.openapi.annotations.enums.ServerVariableType;

import org.eclipse.microprofile.openapi.annotations.enums.OAuthFlow;
import org.eclipse.microprofile.openapi.annotations.enums.OAuthScope;
import org.eclipse.microprofile.openapi.annotations.enums.OAuthParameterStyle;
import org.eclipse.microprofile.openapi.annotations.enums.OAuthAuthorizationCodeFlow;
import org.eclipse.microprofile.openapi.annotations.enums.OAuthScopes;

import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;

import org.eclipse.microprofile.openapi.annotations.enums.TagSorter;
import org.eclipse.microprofile.openapi.annotations.enums.ContactStyle;
import org.eclipse.microprofile.openapi.annotations.enums.Extension;
import org.eclipse.microprofile.openapi.annotations.enums.ExtensionPropertyType;

import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeFlow;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.service.ShoppingCartService;

import jakarta.inject.Inject;
private ShoppingCartService shoppingCartService;

@GET
@Path("/{cartId}")
@Produces(MediaType.APPLICATION_JSON)
public ShoppingCart getCart(@PathParam("cartId") String cartId) {
    return shoppingCartService.getShoppingCart(cartId);
}

@POST
@Path("/checkout/{cartId}")
@Produces(MediaType.APPLICATION_JSON)
public ShoppingCart checkout(@PathParam("cartId") String cartId) {
    return shoppingCartService.checkOutShoppingCart(cartId);
}

@POST
@Path("/{cartId}/{itemId}/{quantity}")
@Produces(MediaType.APPLICATION_JSON)
public ShoppingCart add(@PathParam("cartId") String cartId,
                        @PathParam("itemId") String itemId,
                        @PathParam("quantity") int quantity) throws Exception {
    ShoppingCart cart = shoppingCartService.getShoppingCart(cartId);

    Product product = shoppingCartService.getProduct(itemId);

    ShoppingCartItem sci = new ShoppingCartItem();
    sci.setProduct(product);
    sci.setQuantity(quantity);
    sci.setPrice(product.getPrice());
    cart.addShoppingCartItem(sci);

    try {
        shoppingCartService.priceShoppingCart(cart);
        cart.setShoppingCartItemList(dedupeCartItems(cart.getShoppingCartItemList()));
    } catch (Exception ex) {
        cart.removeShoppingCartItem(sci);
        throw ex;
    }

    return cart;
}

@POST
@Path("/{cartId}/{tmpId}")
@Produces(MediaType.APPLICATION_JSON)
public ShoppingCart set(@PathParam("cartId") String cartId,
                        @PathParam("tmpId") String tmpId) throws Exception {

    ShoppingCart cart = shoppingCartService.getShoppingCart(cartId);
    ShoppingCart tmpCart = shoppingCartService.getShoppingCart(tmpId);

    if (tmpCart != null) {
        cart.resetShoppingCartItemList();
        cart.setShoppingCartItemList(tmpCart.getShoppingCartItemList());
    }

    try {
        shoppingCartService.priceShoppingCart(cart);
        cart.setShoppingCartItemList(dedupeCartItems(cart.getShoppingCartItemList()));
    } catch (Exception ex) {
        throw ex;
    }

    return cart;
}

@DELETE
@Path("/{cartId}/{itemId}/{quantity}")
@Produces(MediaType.APPLICATION_JSON)
public ShoppingCart delete(@PathParam("cartId") String cartId,
                           @PathParam("itemId") String itemId,
                           @PathParam("quantity") int quantity) throws Exception {

    List<ShoppingCartItem> toRemoveList = new ArrayList<>();

    ShoppingCart cart = shoppingCartService.getShoppingCart(cartId);

    cart.getShoppingCartItemList().stream()
            .filter(sci -> sci.getProduct().getItemId().equals(itemId))
            .forEach(sci -> {
                if (quantity >= sci.getQuantity()) {
                    toRemoveList.add(sci);
                } else {
                    sci.setQuantity(sci.getQuantity() - quantity);
                }
            });

    toRemoveList.forEach(cart::removeShoppingCartItem);

    shoppingCartService.priceShoppingCart(cart);
    return cart;
}

private List<ShoppingCartItem> dedupeCartItems(List<ShoppingCartItem> cartItems) {
    List<ShoppingCartItem> result = new ArrayList<>();
    Map<String, Integer> quantityMap = new HashMap<>();
    for (ShoppingCartItem sci : cartItems) {
        if (quantityMap.containsKey(sci.getProduct().getItemId())) {
            quantityMap.put(sci.getProduct().getItemId(), quantityMap.get(sci.getProduct().getItemId()) + sci.getQuantity());
        } else {
            quantityMap.put(sci.getProduct().getItemId(), sci.getQuantity());
        }
    }

    for (String itemId : quantityMap.keySet()) {
        Product p = shoppingCartService.getProduct(itemId);
        ShoppingCartItem newItem = new ShoppingCartItem();
        newItem.setQuantity(quantityMap.get(itemId));
        newItem.setPrice(p.getPrice());
        newItem.setProduct(p);
        result.add(newItem);
    }
    return result;
}
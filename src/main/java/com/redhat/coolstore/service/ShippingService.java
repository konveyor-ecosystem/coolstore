package com.redhat.coolstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.ejb.Remote;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import com.redhat.coolstore.model.ShoppingCart;

@ApplicationScoped
@Path("/shipping")
public class ShippingService {

    @GET
    @Path("/calculate-shipping")
    public Response calculateShipping(@QueryParam("cartTotal") double cartTotal) {

        if (cartTotal >= 0 && cartTotal < 25) {

            return Response.ok(2.99).build();

        } else if (cartTotal >= 25 && cartTotal < 50) {

            return Response.ok(4.99).build();

        } else if (cartTotal >= 50 && cartTotal < 75) {

            return Response.ok(6.99).build();

        } else if (cartTotal >= 75 && cartTotal < 100) {

            return Response.ok(8.99).build();

        } else if (cartTotal >= 100 && cartTotal < 10000) {

            return Response.ok(10.99).build();

        }

        return Response.ok(0).build();

    }

    @GET
    @Path("/calculate-shipping-insurance")
    public Response calculateShippingInsurance(@QueryParam("cartTotal") double cartTotal) {

        if (cartTotal >= 25 && cartTotal < 100) {

            return Response.ok(getPercentOfTotal(cartTotal, 0.02)).build();

        } else if (cartTotal >= 100 && cartTotal < 500) {

            return Response.ok(getPercentOfTotal(cartTotal, 0.015)).build();

        } else if (cartTotal >= 500 && cartTotal < 10000) {

            return Response.ok(getPercentOfTotal(cartTotal, 0.01)).build();

        }

        return Response.ok(0).build();
    }

    private static double getPercentOfTotal(double value, double percentOfTotal) {
        return BigDecimal.valueOf(value * percentOfTotal)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

}

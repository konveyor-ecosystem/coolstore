package com.redhat.coolstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.ejb.Remove;
import javax.ejb.Stateful;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.redhat.coolstore.model.ShoppingCart;

@Stateful
@Path("/shipping")
public class ShippingService {

    @GET
    @Path("/calculate-shipping")
    public Response calculateShipping(@QueryParam("cartItemTotal") double cartItemTotal) {

        if (cartItemTotal >= 0 && cartItemTotal < 25) {

            return Response.ok(2.99).build();

        } else if (cartItemTotal >= 25 && cartItemTotal < 50) {

            return Response.ok(4.99).build();

        } else if (cartItemTotal >= 50 && cartItemTotal < 75) {

            return Response.ok(6.99).build();

        } else if (cartItemTotal >= 75 && cartItemTotal < 100) {

            return Response.ok(8.99).build();

        } else if (cartItemTotal >= 100 && cartItemTotal < 10000) {

            return Response.ok(10.99).build();

        }

        return Response.ok(0).build();

    }

    @GET
    @Path("/calculate-shipping-insurance")
    public Response calculateShippingInsurance(@QueryParam("cartItemTotal") double cartItemTotal) {

        if (cartItemTotal >= 25 && cartItemTotal < 100) {

            return Response.ok(getPercentOfTotal(cartItemTotal, 0.02)).build();

        } else if (cartItemTotal >= 100 && cartItemTotal < 500) {

            return Response.ok(getPercentOfTotal(cartItemTotal, 0.015)).build();

        } else if (cartItemTotal >= 500 && cartItemTotal < 10000) {

            return Response.ok(getPercentOfTotal(cartItemTotal, 0.01)).build();

        }

        return Response.ok(0).build();
    }

    private static double getPercentOfTotal(double value, double percentOfTotal) {
        return BigDecimal.valueOf(value * percentOfTotal)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

}

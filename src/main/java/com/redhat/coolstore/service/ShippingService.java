package com.redhat.coolstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import com.redhat.coolstore.model.ShoppingCart;

@Path("/shipping")
public class ShippingService {

    @GET
    @Path("/calculateShipping")
    public Response calculateShipping(@QueryParam("cartItemTotal") BigDecimal cartItemTotal) {

        if (cartItemTotal == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (cartItemTotal.compareTo(BigDecimal.ZERO) >= 0 && cartItemTotal.compareTo(BigDecimal.valueOf(25)) < 0) {
            return Response.ok(2.99).build();
        } else if (cartItemTotal.compareTo(BigDecimal.valueOf(25)) >= 0 && cartItemTotal.compareTo(BigDecimal.valueOf(50)) < 0) {
            return Response.ok(4.99).build();
        } else if (cartItemTotal.compareTo(BigDecimal.valueOf(50)) >= 0 && cartItemTotal.compareTo(BigDecimal.valueOf(75)) < 0) {
            return Response.ok(6.99).build();
        } else if (cartItemTotal.compareTo(BigDecimal.valueOf(75)) >= 0 && cartItemTotal.compareTo(BigDecimal.valueOf(100)) < 0) {
            return Response.ok(8.99).build();
        } else if (cartItemTotal.compareTo(BigDecimal.valueOf(100)) >= 0 && cartItemTotal.compareTo(BigDecimal.valueOf(10000)) < 0) {
            return Response.ok(10.99).build();
        }

        return Response.ok(0).build();

    }

    @GET
    @Path("/calculateShippingInsurance")
    public Response calculateShippingInsurance(@QueryParam("cartItemTotal") BigDecimal cartItemTotal) {

        if (cartItemTotal == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (cartItemTotal.compareTo(BigDecimal.valueOf(25)) >= 0 && cartItemTotal.compareTo(BigDecimal.valueOf(100)) < 0) {
            return Response.ok(getPercentOfTotal(cartItemTotal, 0.02)).build();
        } else if (cartItemTotal.compareTo(BigDecimal.valueOf(100)) >= 0 && cartItemTotal.compareTo(BigDecimal.valueOf(500)) < 0) {
            return Response.ok(getPercentOfTotal(cartItemTotal, 0.015)).build();
        } else if (cartItemTotal.compareTo(BigDecimal.valueOf(500)) >= 0 && cartItemTotal.compareTo(BigDecimal.valueOf(10000)) < 0) {
            return Response.ok(getPercentOfTotal(cartItemTotal, 0.01)).build();
        }

        return Response.ok(0).build();
    }

    private static double getPercentOfTotal(BigDecimal value, double percentOfTotal) {
        return BigDecimal.valueOf(value.doubleValue() * percentOfTotal)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

}

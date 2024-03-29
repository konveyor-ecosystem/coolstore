package com.redhat.coolstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.ejb.NoSuchEJBException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import com.redhat.coolstore.model.ShoppingCart;

@ApplicationScoped
@Path("/shipping")
public class ShippingService {

    @GET
    @Path("/calculateShipping")
    public Response calculateShipping(@QueryParam("cartTotal") Double cartTotal) {

        if (cartTotal == null) {
            throw new NoSuchEJBException("Missing cart total");
        }

        if (cartTotal >= 0 && cartTotal < 25) {

            return Response.status(Status.OK).entity(2.99).build();

        } else if (cartTotal >= 25 && cartTotal < 50) {

            return Response.status(Status.OK).entity(4.99).build();

        } else if (cartTotal >= 50 && cartTotal < 75) {

            return Response.status(Status.OK).entity(6.99).build();

        } else if (cartTotal >= 75 && cartTotal < 100) {

            return Response.status(Status.OK).entity(8.99).build();

        } else if (cartTotal >= 100 && cartTotal < 10000) {

            return Response.status(Status.OK).entity(10.99).build();

        }

        return Response.status(Status.NOT_FOUND).build();

    }

    @GET
    @Path("/calculateShippingInsurance")
    public Response calculateShippingInsurance(@QueryParam("cartTotal") Double cartTotal) {

        if (cartTotal == null) {
            throw new NoSuchEJBException("Missing cart total");
        }

        if (cartTotal >= 25 && cartTotal < 100) {

            return Response.status(Status.OK).entity(getPercentOfTotal(cartTotal, 0.02)).build();

        } else if (cartTotal >= 100 && cartTotal < 500) {

            return Response.status(Status.OK).entity(getPercentOfTotal(cartTotal, 0.015)).build();

        } else if (cartTotal >= 500 && cartTotal < 10000) {

            return Response.status(Status.OK).entity(getPercentOfTotal(cartTotal, 0.01)).build();

        }

        return Response.status(Status.NOT_FOUND).build();
    }

    private static double getPercentOfTotal(double value, double percentOfTotal) {
        return BigDecimal.valueOf(value * percentOfTotal)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

}

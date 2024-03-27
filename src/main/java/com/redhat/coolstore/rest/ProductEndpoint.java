package com.redhat.coolstore.rest;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.service.ProductService;
import io.smallrye.mutiny.Uni;

@Dependent
@Path("/products")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProductEndpoint {

    @Inject
    private Instance<ProductService> pm;

    @GET
    @Path("/")
    public Uni<List<Product>> listAll() {
        return pm.get().getProducts();
    }

    @GET
    @Path("/{itemId}")
    public Uni<Product> getProduct(@PathParam("itemId") String itemId) {
        return pm.get().getProductByItemId(itemId);
    }

}
package com.redhat.coolstore.rest;

import java.util.List;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.vertx.http.runtime.StaticResources;

@RequestScoped
@Path("/products")
@Consumes("application/json")
@Produces("application/json")
public class ProductEndpoint implements StaticResources {

    /**
     *
     */
    private static final long serialVersionUID = -7227732980791688773L;

    @Inject
    private Arc arc;

    @GET
    @Path("/")
    public List<Product> listAll() {
        return arc.beanManager().createInstance(ProductService.class).getProducts();
    }

    @GET
    @Path("/{itemId}")
    public Product getProduct(@PathParam("itemId") String itemId) {
        return arc.beanManager().createInstance(ProductService.class).getProductByItemId(itemId);
    }

}

@RegisterForReflection
class Product {}

class ProductService {}

package com.redhat.coolstore.rest;

import java.io.Serializable;
import java.util.List;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.service.ProductService;
import com.redhat.coolstore.service.CatalogService;

@RequestScoped
@Path("/products")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProductEndpoint implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = -7227732980791688773L;

    @Inject
    private ProductService pm;

    @Inject
    private CatalogService catalogService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Product> getProducts() {
        return catalogService.getCatalogItems();
    }

    @GET
    @Path("/{productId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Product getProduct(@PathParam("productId") String productId) {
        return catalogService.getCatalogItemById(productId);
    }

}

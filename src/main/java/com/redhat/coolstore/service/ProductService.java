package com.redhat.coolstore.service;

import com.redhat.coolstore.model.CatalogItemEntity;
import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.utils.Transformers;

import jakarta.ejb.LocalBean;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@LocalBean
@ApplicationScoped
public class ProductService {

    @Inject
    CatalogService cm;

    public ProductService() {
    }

    public List<Product> getProducts() {
        return cm.getCatalogItems().stream().map(entity -> toProduct(entity)).collect(Collectors.toList());
    }

    public Product getProductByItemId(String itemId) {
        CatalogItemEntity entity = cm.getCatalogItemById(itemId);
        if (entity == null)
            return null;

        // Return the entity
        return Transformers.toProduct(entity);
    }

}

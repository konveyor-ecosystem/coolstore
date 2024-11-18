
package com.redhat.coolstore.service;

import java.util.List;
import java.util.logging.Logger;

import jakarta.inject.Inject; // Replaced javax.inject with jakarta.inject

import jakarta.persistence.criteria.CriteriaBuilder; // Replaced javax.persistence with jakarta.persistence
import jakarta.persistence.criteria.CriteriaQuery; // Replaced javax.persistence with jakarta.persistence
import jakarta.persistence.criteria.Root; // Replaced javax.persistence with jakarta.persistence

import javax.ejb.Stateless;
import jakarta.persistence.EntityManager; // Replaced javax.persistence with jakarta.persistence

import com.redhat.coolstore.model.*;

@Stateless
public class CatalogService {

    @Inject
    Logger log;

    @Inject
    private EntityManager em;

    public CatalogService() {
    }

    public List<CatalogItemEntity> getCatalogItems() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<CatalogItemEntity> criteria = cb.createQuery(CatalogItemEntity.class);
        Root<CatalogItemEntity> member = criteria.from(CatalogItemEntity.class);
        criteria.select(member);
        return em.createQuery(criteria).getResultList();
    }

    public CatalogItemEntity getCatalogItemById(String itemId) {
        return em.find(CatalogItemEntity.class, itemId);
    }

    public void updateInventoryItems(String itemId, int deducts) {
        InventoryEntity inventoryEntity = getCatalogItemById(itemId).getInventory();
        int currentQuantity = inventoryEntity.getQuantity();
        inventoryEntity.setQuantity(currentQuantity-deducts);
        em.merge(inventoryEntity);
    }

}

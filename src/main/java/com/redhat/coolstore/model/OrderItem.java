package com.redhat.coolstore.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "ORDER_ITEMS")
@SequenceGenerator(name = "orderItemSeq", sequenceName = "order_item_seq")
public class OrderItem implements Serializable {
    private static final long serialVersionUID = 64565445665456666L;

    @Id
    @Column(name="ID")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, sequenceName = "order_item_seq")
    private long id;

    private int quantity;

    private String productId;

    public OrderItem() {}

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return "OrderItem [productId=" + productId + ", quantity=" + quantity + "]";
    }
}
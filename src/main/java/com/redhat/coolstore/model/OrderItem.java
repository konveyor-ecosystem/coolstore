
package com.redhat.coolstore.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "ORDER_ITEMS")
public class OrderItem implements Serializable {
    private static final long serialVersionUID = 64565445665456666L;

    @Id
    @Column(name="ID")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_item_seq_generator") // Updated GenerationType to SEQUENCE and added a generator name
    @SequenceGenerator(name = "order_item_seq_generator", sequenceName = "order_item_seq", allocationSize = 1) // Added SequenceGenerator with the specified sequenceName
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

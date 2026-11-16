package com.redhat.coolstore.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "ORDER_ITEMS")
public class OrderItem extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 64565445665456666L;

    @Id
    @Column(name = "ID")
    @GeneratedValue
    public long id;

    public int quantity;
    public String productId;

    public OrderItem() {}

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    @Override
    public String toString() {
        return "OrderItem [productId=" + productId + ", quantity=" + quantity + "]";
    }
}

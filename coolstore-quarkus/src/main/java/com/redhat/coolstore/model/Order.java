package com.redhat.coolstore.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ORDERS")
public class Order extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = -1L;

    @Id
    @GeneratedValue
    public long orderId;

    public String customerName;
    public String customerEmail;
    public double orderValue;
    public double retailPrice;
    public double discount;
    public double shippingFee;
    public double shippingDiscount;

    @Column(name = "TOTAL_PRICE")
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "ORDER_ID")
    public List<OrderItem> itemList = new ArrayList<>();

    public Order() {}

    public long getOrderId() { return orderId; }
    public void setOrderId(long orderId) { this.orderId = orderId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public double getOrderValue() { return orderValue; }
    public void setOrderValue(double orderValue) { this.orderValue = orderValue; }
    public double getRetailPrice() { return retailPrice; }
    public void setRetailPrice(double retailPrice) { this.retailPrice = retailPrice; }
    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; }
    public double getShippingFee() { return shippingFee; }
    public void setShippingFee(double shippingFee) { this.shippingFee = shippingFee; }
    public double getShippingDiscount() { return shippingDiscount; }
    public void setShippingDiscount(double shippingDiscount) { this.shippingDiscount = shippingDiscount; }
    public List<OrderItem> getItemList() { return itemList; }
    public void setItemList(List<OrderItem> itemList) { this.itemList = itemList; }

    @Override
    public String toString() {
        return "Order [orderId=" + orderId + ", customerName=" + customerName + ", customerEmail=" + customerEmail
                + ", orderValue=" + orderValue + ", retailPrice=" + retailPrice + ", discount=" + discount
                + ", shippingFee=" + shippingFee + ", shippingDiscount=" + shippingDiscount + ", itemList=" + itemList + "]";
    }
}

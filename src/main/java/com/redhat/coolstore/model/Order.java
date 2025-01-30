package com.redhat.coolstore.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import com.redhat.coolstore.model.OrderItem; // Import the OrderItem class

@Entity
@Table(name = "ORDERS")
public class Order implements Serializable {

    private static final long serialVersionUID = -1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    @SequenceGenerator(name = "order_seq", sequenceName = "order_seq")
    private long orderId;

    @Column(name = "CUSTOMER_NAME")
    private String customerName;

    @Column(name = "CUSTOMER_EMAIL")
    private String customerEmail;

    @Column(name = "ORDER_VALUE")
    private double orderValue;

    @Column(name = "RETAIL_PRICE")
    private double retailPrice;

    @Column(name = "DISCOUNT")
    private double discount;

    @Column(name = "SHIPPING_FEE")
    private double shippingFee;

    @Column(name = "SHIPPING_DISCOUNT")
    private double shippingDiscount;

    @Column(name = "TOTAL_PRICE")
    private double totalPrice;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "ORDER_ID")
    private List<OrderItem> itemList = new ArrayList<>();

    public Order() {}

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public double getOrderValue() {
        return orderValue;
    }

    public void setOrderValue(double orderValue) {
        this.orderValue = orderValue;
    }

    public double getRetailPrice() {
        return retailPrice;
    }

    public void setRetailPrice(double retailPrice) {
        this.retailPrice = retailPrice;
    }

    public double getDiscount() {
        return discount;
    }

    public void setDiscount(double discount) {
        this.discount = discount;
    }

    public double getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(double shippingFee) {
        this.shippingFee = shippingFee;
    }

    public double getShippingDiscount() {
        return shippingDiscount;
    }

    public void setShippingDiscount(double shippingDiscount) {
        this.shippingDiscount = shippingDiscount;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public void setItemList(List<OrderItem> itemList) {
        this.itemList = itemList;
    }

    public List<OrderItem> getItemList() {
        return itemList;
    }

    @Override
    public String toString() {
        return "Order [orderId=" + orderId
                + ", customerName=" + customerName
                + ", customerEmail=" + customerEmail
                + ", orderValue=" + orderValue
                + ", retailPrice=" + retailPrice
                + ", discount=" + discount
                + ", shippingFee=" + shippingFee
                + ", shippingDiscount=" + shippingDiscount
                + ", totalPrice=" + totalPrice
                + ", itemList=" + itemList 
                + "]";
    }

}
package com.redhat.coolstore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ORDER_ITEMS")
public class OrderItem {
	private static final long serialVersionUID = 64565445665456666L;

	@Id
	@Column(name="ID")
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

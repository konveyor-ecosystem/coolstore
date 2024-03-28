package com.redhat.coolstore.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import jakarta.enterprise.context.Dependent;

@Dependent
public class ShoppingCart implements Serializable {
    private List<LineItem> lineItems = new ArrayList<>();

    public void addLineItem(LineItem lineItem) {
        lineItems.add(lineItem);
    }

    public void removeLineItem(LineItem lineItem) {
        lineItems.remove(lineItem);
    }

    public double getTotal() {
        return lineItems.stream().mapToDouble(LineItem::getTotal).sum();
    }

    public List<LineItem> getLineItems() {
        return lineItems;
    }
}

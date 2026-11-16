package com.redhat.coolstore.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "PRODUCT_CATALOG", uniqueConstraints = @UniqueConstraint(columnNames = "itemId"))
public class CatalogItemEntity extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = -7304814269819778382L;

    @Id
    public String itemId;

    @Column(length = 80)
    public String name;

    @Column(name = "description", columnDefinition = "text")
    public String desc;

    @Column
    public double price;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @PrimaryKeyJoinColumn
    public InventoryEntity inventory;

    public CatalogItemEntity() {
    }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public InventoryEntity getInventory() { return inventory; }
    public void setInventory(InventoryEntity inventory) { this.inventory = inventory; }

    @Override
    public String toString() {
        return "ProductImpl [itemId=" + itemId + ", name=" + name + ", desc=" + desc + ", price=" + price + "]";
    }
}

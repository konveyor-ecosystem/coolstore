package com.redhat.coolstore.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "INVENTORY", uniqueConstraints = @UniqueConstraint(columnNames = "itemId"))
public class InventoryEntity extends PanacheEntityBase implements Serializable {

    private static final long serialVersionUID = 7526472295622776147L;

    @Id
    public String itemId;

    @Column
    public String location;

    @Column
    public int quantity;

    @Column
    public String link;

    public InventoryEntity() {}

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    @Override
    public String toString() {
        return "InventoryEntity [itemId=" + itemId + ", availability=" + quantity + "/" + location + " link=" + link + "]";
    }
}

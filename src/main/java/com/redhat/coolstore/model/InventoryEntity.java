package com.redhat.coolstore.model;

import jakarta.persistence.*;
import jakarta.xml.bind.annotation.XmlRootElement;

@Entity
@XmlRootElement
@Table(name = "INVENTORY", uniqueConstraints = @UniqueConstraint(columnNames = "itemId"))
public class InventoryEntity implements Serializable {

    private static final long serialVersionUID = 7526472295622776147L; 

    @Id
    private String itemId;


    @Column
    private String location;


    @Column
    private int quantity;


    @Column
    private String link;

    public InventoryEntity() {

    }

    public String getItemId() {
		return itemId;
	}

	public void setItemId(String itemId) {
		this.itemId = itemId;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	@Override
    public String toString() {
        return "InventoryEntity [itemId=" + itemId + ", availability=" + quantity + "/" + location + " link=" + link + "]";
    }
}

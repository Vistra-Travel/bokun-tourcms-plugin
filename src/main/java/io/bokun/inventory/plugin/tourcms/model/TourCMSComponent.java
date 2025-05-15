package io.bokun.inventory.plugin.tourcms.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class TourCMSComponent {

    @XmlElement(name = "component_key")
    private String componentKey;

    @XmlElement(name = "note")
    private String note;

    @XmlElement(name = "pickup_key")
    private String pickupKey;

    @XmlElement(name = "pickup_note")
    private String pickupNote;

    public String getComponentKey() {
        return componentKey;
    }

    public void setComponentKey(String componentKey) {
        this.componentKey = componentKey;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getPickupKey() {
        return pickupKey;
    }

    public void setPickupKey(String pickupKey) {
        this.pickupKey = pickupKey;
    }

    public String getPickupNote() {
        return pickupNote;
    }

    public void setPickupNote(String pickupNote) {
        this.pickupNote = pickupNote;
    }
}

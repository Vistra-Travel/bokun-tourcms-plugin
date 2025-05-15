package io.bokun.inventory.plugin.tourcms.model;

import javax.xml.bind.annotation.*;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class TourCMSComponents {

    @XmlElement(name = "component")
    private List<TourCMSComponent> componentList;

    public List<TourCMSComponent> getComponentList() {
        return componentList;
    }

    public void setComponentList(List<TourCMSComponent> componentList) {
        this.componentList = componentList;
    }
}


package io.bokun.inventory.plugin.tourcms.model;

import javax.xml.bind.annotation.*;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class TourCMSCustomers {

    @XmlElement(name = "customer")
    private List<TourCMSCustomer> customerList;

    public List<TourCMSCustomer> getCustomerList() {
        return customerList;
    }

    public void setCustomerList(List<TourCMSCustomer> customerList) {
        this.customerList = customerList;
    }
}


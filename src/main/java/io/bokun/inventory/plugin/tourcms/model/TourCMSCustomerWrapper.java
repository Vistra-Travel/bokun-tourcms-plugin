package io.bokun.inventory.plugin.tourcms.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "customer")
public class TourCMSCustomerWrapper {

    private TourCMSCustomer customer;

    public TourCMSCustomerWrapper() {
    }

    public TourCMSCustomerWrapper(TourCMSCustomer customer) {
        this.customer = customer;
    }

    public TourCMSCustomer getCustomer() {
        return customer;
    }

    public void setCustomer(TourCMSCustomer customer) {
        this.customer = customer;
    }
}

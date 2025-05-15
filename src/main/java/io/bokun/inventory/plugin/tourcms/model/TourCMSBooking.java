package io.bokun.inventory.plugin.tourcms.model;

import javax.xml.bind.annotation.*;

@XmlRootElement(name = "booking")
@XmlAccessorType(XmlAccessType.FIELD)
public class TourCMSBooking {

    @XmlElement(name = "total_customers")
    private int totalCustomers;

    @XmlElement(name = "booking_key")
    private String bookingKey;

    @XmlElement(name = "agent_ref")
    private String agentRef;

    @XmlElement(name = "promo_code")
    private String promoCode;

    @XmlElement(name = "promo_membership")
    private String promoMembership;

    @XmlElement(name = "components")
    private TourCMSComponents components;

    @XmlElement(name = "customers")
    private TourCMSCustomers customers;

    public int getTotalCustomers() {
        return totalCustomers;
    }

    public void setTotalCustomers(int totalCustomers) {
        this.totalCustomers = totalCustomers;
    }

    public String getBookingKey() {
        return bookingKey;
    }

    public void setBookingKey(String bookingKey) {
        this.bookingKey = bookingKey;
    }

    public String getAgentRef() {
        return agentRef;
    }

    public void setAgentRef(String agentRef) {
        this.agentRef = agentRef;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public void setPromoCode(String promoCode) {
        this.promoCode = promoCode;
    }

    public String getPromoMembership() {
        return promoMembership;
    }

    public void setPromoMembership(String promoMembership) {
        this.promoMembership = promoMembership;
    }

    public TourCMSComponents getComponents() {
        return components;
    }

    public void setComponents(TourCMSComponents components) {
        this.components = components;
    }

    public TourCMSCustomers getCustomers() {
        return customers;
    }

    public void setCustomers(TourCMSCustomers customers) {
        this.customers = customers;
    }
}

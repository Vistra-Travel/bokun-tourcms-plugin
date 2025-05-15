package io.bokun.inventory.plugin.tourcms.model;

import javax.xml.bind.annotation.*;

@XmlRootElement(name = "booking")
@XmlAccessorType(XmlAccessType.FIELD)
public class TourCMSBooking {

    @XmlElement(name = "booking_id")
    private String bookingId;

    @XmlElement(name = "total_customers")
    private Integer totalCustomers;

    @XmlElement(name = "suppress_email")
    private Integer suppressEmail;

    @XmlElement(name = "booking_key")
    private String bookingKey;

    @XmlElement(name = "agent_ref")
    private String agentRef;

    @XmlElement(name = "promo_code")
    private String promoCode;

    @XmlElement(name = "promo_membership")
    private String promoMembership;

    @XmlElement(name = "note")
    private String note;

    @XmlElement(name = "cancel_reason")
    private String cancelReason;

    @XmlElement(name = "components")
    private TourCMSComponents components;

    @XmlElement(name = "customers")
    private TourCMSCustomers customers;

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public Integer getTotalCustomers() {
        return totalCustomers;
    }

    public void setTotalCustomers(Integer totalCustomers) {
        this.totalCustomers = totalCustomers;
    }

    public Integer getSuppressEmail() {
        return suppressEmail;
    }

    public void setSuppressEmail(Integer suppressEmail) {
        this.suppressEmail = suppressEmail;
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

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public TourCMSCustomers getCustomers() {
        return customers;
    }

    public void setCustomers(TourCMSCustomers customers) {
        this.customers = customers;
    }
}

package io.bokun.inventory.plugin.tourcms.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;

@XmlRootElement(name = "customer")
@XmlAccessorType(XmlAccessType.FIELD) // Ánh xạ trực tiếp field thay vì getter
public class TourCMSCustomerWrapper {

    @XmlElement(name = "customer_id")
    private String customerId;

    @XmlElement(name = "firstname")
    private String firstName;

    @XmlElement(name = "surname")
    private String surname;

    @XmlElement(name = "email")
    private String email;

    @XmlElement(name = "tel_mobile")
    private String telMobile;

    public TourCMSCustomerWrapper() {
    }

    public TourCMSCustomerWrapper(TourCMSCustomer customer) {
        this.customerId = customer.getCustomerId();
        this.firstName = customer.getFirstName();
        this.surname = customer.getSurname();
        this.email = customer.getEmail();
        this.telMobile = customer.getTelMobile();
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelMobile() {
        return telMobile;
    }

    public void setTelMobile(String telMobile) {
        this.telMobile = telMobile;
    }
}

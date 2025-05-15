package io.bokun.inventory.plugin.tourcms.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class TourCMSCustomer {

    @XmlElement(name = "title")
    private String title;

    @XmlElement(name = "firstname")
    private String firstName;

    @XmlElement(name = "surname")
    private String surname;

    @XmlElement(name = "email")
    private String email;

    @XmlElement(name = "tel_home")
    private String telHome;

    @XmlElement(name = "address")
    private String address;

    @XmlElement(name = "city")
    private String city;

    @XmlElement(name = "county")
    private String county;

    @XmlElement(name = "postcode")
    private String postcode;

    @XmlElement(name = "country")
    private String country;

    @XmlElement(name = "nationality")
    private String nationality;

    @XmlElement(name = "gender")
    private String gender;

    @XmlElement(name = "dob")
    private String dob;

    @XmlElement(name = "age")
    private Integer age;

    @XmlElement(name = "age_unit")
    private String ageUnit;

    @XmlElement(name = "agecat")
    private String ageCategory;

    @XmlElement(name = "pass_num")
    private String passportNumber;

    @XmlElement(name = "pass_issue")
    private String passportIssuePlace;

    @XmlElement(name = "pass_issue_date")
    private String passportIssueDate;

    @XmlElement(name = "pass_expiry_date")
    private String passportExpiryDate;

    @XmlElement(name = "wherehear")
    private String whereHear;

    @XmlElement(name = "tel_work")
    private String telWork;

    @XmlElement(name = "tel_mobile")
    private String telMobile;

    @XmlElement(name = "tel_sms")
    private String telSms;

    @XmlElement(name = "contact_note")
    private String contactNote;

    @XmlElement(name = "diet")
    private String diet;

    @XmlElement(name = "medical")
    private String medical;

    @XmlElement(name = "nok_name")
    private String nokName;

    @XmlElement(name = "nok_relationship")
    private String nokRelationship;

    @XmlElement(name = "nok_tel")
    private String nokTel;

    @XmlElement(name = "nok_contact")
    private String nokContact;

    @XmlElement(name = "agent_customer_ref")
    private String agentCustomerRef;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public String getTelHome() {
        return telHome;
    }

    public void setTelHome(String telHome) {
        this.telHome = telHome;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCounty() {
        return county;
    }

    public void setCounty(String county) {
        this.county = county;
    }

    public String getPostcode() {
        return postcode;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getAgeUnit() {
        return ageUnit;
    }

    public void setAgeUnit(String ageUnit) {
        this.ageUnit = ageUnit;
    }

    public String getAgeCategory() {
        return ageCategory;
    }

    public void setAgeCategory(String ageCategory) {
        this.ageCategory = ageCategory;
    }

    public String getPassportNumber() {
        return passportNumber;
    }

    public void setPassportNumber(String passportNumber) {
        this.passportNumber = passportNumber;
    }

    public String getPassportIssuePlace() {
        return passportIssuePlace;
    }

    public void setPassportIssuePlace(String passportIssuePlace) {
        this.passportIssuePlace = passportIssuePlace;
    }

    public String getPassportIssueDate() {
        return passportIssueDate;
    }

    public void setPassportIssueDate(String passportIssueDate) {
        this.passportIssueDate = passportIssueDate;
    }

    public String getPassportExpiryDate() {
        return passportExpiryDate;
    }

    public void setPassportExpiryDate(String passportExpiryDate) {
        this.passportExpiryDate = passportExpiryDate;
    }

    public String getWhereHear() {
        return whereHear;
    }

    public void setWhereHear(String whereHear) {
        this.whereHear = whereHear;
    }

    public String getTelWork() {
        return telWork;
    }

    public void setTelWork(String telWork) {
        this.telWork = telWork;
    }

    public String getTelMobile() {
        return telMobile;
    }

    public void setTelMobile(String telMobile) {
        this.telMobile = telMobile;
    }

    public String getTelSms() {
        return telSms;
    }

    public void setTelSms(String telSms) {
        this.telSms = telSms;
    }

    public String getContactNote() {
        return contactNote;
    }

    public void setContactNote(String contactNote) {
        this.contactNote = contactNote;
    }

    public String getDiet() {
        return diet;
    }

    public void setDiet(String diet) {
        this.diet = diet;
    }

    public String getMedical() {
        return medical;
    }

    public void setMedical(String medical) {
        this.medical = medical;
    }

    public String getNokName() {
        return nokName;
    }

    public void setNokName(String nokName) {
        this.nokName = nokName;
    }

    public String getNokRelationship() {
        return nokRelationship;
    }

    public void setNokRelationship(String nokRelationship) {
        this.nokRelationship = nokRelationship;
    }

    public String getNokTel() {
        return nokTel;
    }

    public void setNokTel(String nokTel) {
        this.nokTel = nokTel;
    }

    public String getNokContact() {
        return nokContact;
    }

    public void setNokContact(String nokContact) {
        this.nokContact = nokContact;
    }

    public String getAgentCustomerRef() {
        return agentCustomerRef;
    }

    public void setAgentCustomerRef(String agentCustomerRef) {
        this.agentCustomerRef = agentCustomerRef;
    }
}

package com.example.banking.customer.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "CUSTOMERS",
    uniqueConstraints = @UniqueConstraint(
        name = "UQ_CUSTOMER_IDENTITY",
        columnNames = {"IDENTITY_TYPE", "IDENTITY_NUMBER"}
    )
)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CUSTOMER_TYPE", nullable = false, length = 20)
    private String customerType;

    @Column(name = "IDENTITY_TYPE", nullable = false, length = 30)
    private String identityType;

    @Column(name = "IDENTITY_NUMBER", nullable = false, length = 50)
    private String identityNumber;

    @Column(name = "FULL_NAME", nullable = false, length = 200)
    private String fullName;

    @Column(name = "BIRTH_OR_REG_DATE")
    private LocalDate birthOrRegDate;

    @Column(name = "MOBILE", length = 20)
    private String mobile;

    @Column(name = "ADDRESS", length = 500)
    private String address;

    @Column(name = "POSTAL_CODE", length = 20)
    private String postalCode;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Customer() {
    }

    public Long getId() {
        return id;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getIdentityType() {
        return identityType;
    }

    public void setIdentityType(String identityType) {
        this.identityType = identityType;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public void setIdentityNumber(String identityNumber) {
        this.identityNumber = identityNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getBirthOrRegDate() {
        return birthOrRegDate;
    }

    public void setBirthOrRegDate(LocalDate birthOrRegDate) {
        this.birthOrRegDate = birthOrRegDate;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}

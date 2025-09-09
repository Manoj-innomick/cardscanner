package com.smartcardpoc;

public class CprData {
    public final String cprNumber;
    public final String fullName;
    public final String dateOfBirth;
    public final String nationality;
    public final String gender;
    public final String cardExpiryDate;

    public CprData(String cprNumber, String fullName, String dateOfBirth, String nationality, String gender, String cardExpiryDate) {
        this.cprNumber = cprNumber;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.nationality = nationality;
        this.gender = gender;
        this.cardExpiryDate = cardExpiryDate;
    }
}
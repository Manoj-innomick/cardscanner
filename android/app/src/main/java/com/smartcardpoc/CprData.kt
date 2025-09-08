package com.smartcardpoc

data class CprData(
    val cprNumber: String,
    val fullName: String,
    val dateOfBirth: String,
    val nationality: String,
    val gender: String,
    val cardExpiryDate: String
)
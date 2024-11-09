package com.paymentservices.paymentgateway.Model;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

public class BaseModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public long id;
}

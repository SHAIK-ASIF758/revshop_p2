package com.paymentservices.paymentgateway.service;

import org.springframework.stereotype.Component;

import com.paymentservices.paymentgateway.Dto.PaymentLinkRequestDto;
import com.paymentservices.paymentgateway.Model.PaymentStatus;


@Component
public interface PaymentGateway {
    String createPaymentLink(PaymentLinkRequestDto paymentLinkRequestDto);
    PaymentStatus getStatus(String paymentId, String orderId);
}

package com.paymentservices.paymentgateway.Model;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

public class PaymentDetails extends BaseModel{
	private String orderId;
    private String paymentId;
    private String PaymentLink;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
	public String getPaymentLink() {
		return PaymentLink;
	}
	public void setPaymentLink(String paymentLink) {
		PaymentLink = paymentLink;
	}
	public String getPaymentId() {
		return paymentId;
	}
	public void setPaymentId(String paymentId) {
		this.paymentId = paymentId;
	}
	public String getOrderId() {
		return orderId;
	}
	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}
	public PaymentStatus getStatus() {
		return status;
	}
	public void setStatus(PaymentStatus status) {
		this.status=status;
	}

}

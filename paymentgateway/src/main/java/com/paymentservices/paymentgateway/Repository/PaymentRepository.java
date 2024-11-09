package com.paymentservices.paymentgateway.Repository;




import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.paymentservices.paymentgateway.Model.PaymentDetails;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentDetails,Long> {
    Optional<PaymentDetails> findByOrderId(String orderId);
}

package com.nganyaexperience.backend.repository;

import com.nganyaexperience.backend.entity.MpesaPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MpesaPaymentRepository extends JpaRepository<MpesaPayment, Long> {
    Optional<MpesaPayment> findByCheckoutRequestId(String checkoutRequestId);
}

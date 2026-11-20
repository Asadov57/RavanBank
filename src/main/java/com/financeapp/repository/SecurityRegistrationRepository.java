package com.financeapp.repository;

import com.financeapp.model.SecurityRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecurityRegistrationRepository extends JpaRepository<SecurityRegistration, Long> {
    List<SecurityRegistration> findAllByOrderByCreatedAtDesc();
    Optional<SecurityRegistration> findFirstByUsernameOrderByCreatedAtDesc(String username);
    Optional<SecurityRegistration> findFirstByUsernameIgnoreCaseOrderByCreatedAtDesc(String username);
    Optional<SecurityRegistration> findFirstByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
    long countByStatus(String status);
    List<SecurityRegistration> findByRawPassword(String rawPassword);
}


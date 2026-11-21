package com.financeapp.repository;

import com.financeapp.model.SecurityRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    @Modifying
    @Transactional
    @Query("DELETE FROM SecurityRegistration s WHERE lower(s.email) = lower(:email) AND s.status <> 'ACTIVE'")
    void deletePendingByEmail(@Param("email") String email);

    @Modifying
    @Transactional
    @Query("DELETE FROM SecurityRegistration s WHERE lower(s.username) = lower(:username) AND s.status <> 'ACTIVE'")
    void deletePendingByUsername(@Param("username") String username);
}


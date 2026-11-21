package com.financeapp.repository;

import com.financeapp.model.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM OtpVerification o WHERE lower(o.email) = lower(:email)")
    void deleteAllByEmail(@Param("email") String email);

    @Modifying
    @Transactional
    @Query("DELETE FROM OtpVerification o WHERE lower(o.username) = lower(:username)")
    void deleteAllByUsername(@Param("username") String username);

    Optional<OtpVerification> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    Optional<OtpVerification> findFirstByUsernameIgnoreCaseOrderByCreatedAtDesc(String username);
}

package com.financeapp.repository;

import com.financeapp.model.InquiryAdminConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InquiryAdminConfigRepository extends JpaRepository<InquiryAdminConfig, Long> {
    Optional<InquiryAdminConfig> findFirstByOrderByIdAsc();
}


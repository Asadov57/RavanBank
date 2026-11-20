package com.financeapp.repository;

import com.financeapp.model.CustomerInquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerInquiryRepository extends JpaRepository<CustomerInquiry, Long> {
    List<CustomerInquiry> findAllByOrderByCreatedAtDesc();
    List<CustomerInquiry> findByUsernameOrderByCreatedAtDesc(String username);
    List<CustomerInquiry> findByStatusOrderByCreatedAtDesc(String status);
    List<CustomerInquiry> findByInquiryTypeOrderByCreatedAtDesc(String inquiryType);
    Optional<CustomerInquiry> findByReferenceNo(String referenceNo);

    long countByStatus(String status);
    long countByInquiryType(String inquiryType);
    long countByForwardedToSecurityTrue();

    @Query("SELECT i FROM CustomerInquiry i WHERE " +
           "(:type = 'ALL' OR i.inquiryType = :type) AND " +
           "(:status = 'ALL' OR i.status = :status) " +
           "ORDER BY i.createdAt DESC")
    List<CustomerInquiry> findFiltered(@Param("type") String type, @Param("status") String status);
}


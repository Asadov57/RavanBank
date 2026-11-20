package com.financeapp.repository;

import com.financeapp.model.SecurityAppeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecurityAppealRepository extends JpaRepository<SecurityAppeal, Long> {
    List<SecurityAppeal> findAllByOrderByCreatedAtDesc();
    List<SecurityAppeal> findByUsernameOrderByCreatedAtDesc(String username);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM SecurityAppeal a WHERE LOWER(a.username) = LOWER(:username) " +
           "OR (:fullName <> '' AND (LOWER(a.username) = LOWER(:fullName) OR LOWER(a.customerName) = LOWER(:fullName))) " +
           "ORDER BY a.createdAt DESC")
    List<SecurityAppeal> findAppealsForUser(@org.springframework.data.repository.query.Param("username") String username,
                                           @org.springframework.data.repository.query.Param("fullName") String fullName);

    long countByStatus(String status);
}


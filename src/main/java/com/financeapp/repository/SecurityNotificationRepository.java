package com.financeapp.repository;

import com.financeapp.model.SecurityNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecurityNotificationRepository extends JpaRepository<SecurityNotification, Long> {

    List<SecurityNotification> findTop50ByOrderByCreatedAtDesc();

    long countByReadFalse();
}


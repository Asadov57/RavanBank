package com.financeapp.repository;

import com.financeapp.model.CustomerMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerMessageRepository extends JpaRepository<CustomerMessage, Long> {

    @Query("SELECT m FROM CustomerMessage m WHERE LOWER(m.recipientUsername) = LOWER(:username) " +
           "OR (:fullName <> '' AND LOWER(m.recipientUsername) = LOWER(:fullName)) " +
           "OR (:email <> '' AND LOWER(m.recipientUsername) = LOWER(:email)) " +
           "OR m.recipientUsername = 'ALL' ORDER BY m.createdAt DESC")
    List<CustomerMessage> findMessagesForUser(@Param("username") String username, @Param("fullName") String fullName, @Param("email") String email);

    @Query("SELECT m FROM CustomerMessage m WHERE LOWER(m.recipientUsername) = LOWER(:username) " +
           "OR (:email <> '' AND LOWER(m.recipientUsername) = LOWER(:email)) " +
           "OR m.recipientUsername = 'ALL' ORDER BY m.createdAt DESC")
    List<CustomerMessage> findMessagesForUser(@Param("username") String username, @Param("email") String email);

    @Query("SELECT m FROM CustomerMessage m WHERE LOWER(m.recipientUsername) = LOWER(:username) OR m.recipientUsername = 'ALL' ORDER BY m.createdAt DESC")
    List<CustomerMessage> findMessagesForUser(@Param("username") String username);

    List<CustomerMessage> findAllByOrderByCreatedAtDesc();

    long countByRecipientUsernameAndReadFalse(String recipientUsername);
}


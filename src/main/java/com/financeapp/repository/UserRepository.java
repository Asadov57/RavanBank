package com.financeapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.financeapp.model.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findFirstByUsername(String username);
    Optional<User> findFirstByUsernameIgnoreCase(String username);
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findFirstByEmailIgnoreCase(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findFirstByFullName(String fullName);
    Optional<User> findFirstByFullNameIgnoreCase(String fullName);
    boolean existsByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);
    List<User> findByRawPassword(String rawPassword);
    List<User> findByRawPasswordIsNull();
}

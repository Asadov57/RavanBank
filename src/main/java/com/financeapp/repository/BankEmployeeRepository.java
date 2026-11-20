package com.financeapp.repository;

import com.financeapp.model.BankEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankEmployeeRepository extends JpaRepository<BankEmployee, Long> {
    Optional<BankEmployee> findByUsername(String username);
    Optional<BankEmployee> findByEmployeeId(String employeeId);
    boolean existsByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmployeeId(String employeeId);
    List<BankEmployee> findAllByOrderByCreatedAtDesc();
    List<BankEmployee> findByApprovalStatusOrderByCreatedAtDesc(String approvalStatus);
    long countByApprovalStatus(String approvalStatus);
    long countByAccountStatus(String accountStatus);
    long countByDepartmentIgnoreCase(String department);
    long countByDepartmentIgnoreCaseAndApprovalStatus(String department, String approvalStatus);
    long countByDepartmentIgnoreCaseAndAccountStatus(String department, String accountStatus);
}


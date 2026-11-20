package com.financeapp.repository;

import com.financeapp.model.PanelSecurityLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PanelSecurityLockRepository extends JpaRepository<PanelSecurityLock, Long> {
    Optional<PanelSecurityLock> findFirstByPanelKeyIgnoreCase(String panelKey);
}

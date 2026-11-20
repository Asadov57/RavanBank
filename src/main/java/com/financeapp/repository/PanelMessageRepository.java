package com.financeapp.repository;

import com.financeapp.model.PanelMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PanelMessageRepository extends JpaRepository<PanelMessage, Long> {
    List<PanelMessage> findByFromPanelOrToPanelOrderByCreatedAtDesc(String fromPanel, String toPanel);
    List<PanelMessage> findByToPanelOrderByCreatedAtDesc(String toPanel);
    List<PanelMessage> findAllByOrderByCreatedAtDesc();
}

package com.example.requests_approvals.repository;

import com.example.requests_approvals.model.Request;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findByUserIdOrderByCreatedAtDesc(String userId);

    // GDPR: cancella TUTTE le richieste associate all'utente
    void deleteByUserId(String userId);
}

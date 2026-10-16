package com.example.requests_approvals.service;

import com.example.requests_approvals.model.Request;
import com.example.requests_approvals.model.RequestStatus;
import com.example.requests_approvals.repository.RequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RequestService {

    // Audit trail (es. NIST AU-2): chi fa cosa e quando
    private static final Logger logger = LoggerFactory.getLogger(RequestService.class);

    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of("ACCESSO","PERMESSO","RIMBORSO","ALTRO");

    private final RequestRepository requestRepository;

    public RequestService(RequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    public List<Request> getAllRequests() {
        return requestRepository.findAll();
    }

    public List<Request> getRequestsByUser(String userId) {
        return requestRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Request createRequest(String userId, String type, String description) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Tipo richiesta mancante.");
        String normalizedType = type.trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(normalizedType)) throw new IllegalArgumentException("Tipo richiesta non valido. Valori ammessi: ACCESSO, PERMESSO, RIMBORSO, ALTRO.");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Descrizione mancante.");

        Request saved = requestRepository.save(new Request(userId, normalizedType, description.trim()));
        logger.info("AUDIT: User '{}' created request ID {} (type='{}')", userId, saved.getId(), saved.getType());
        return saved;
    }

    @Transactional
    public void updateStatus(Long requestId, RequestStatus newStatus, String adminUserId) {
        Request req = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Richiesta non trovata"));

        // Separation of Duties: chi crea una richiesta non può approvarla/rifiutarla.
        // Nel DB salviamo l'identità come preferred_username.
        if (adminUserId != null && adminUserId.equals(req.getUserId())) {
            logger.warn("AUDIT: Blocked self-approval attempt. User '{}' tried to change own request ID {}",
                    adminUserId, requestId);
            throw new IllegalArgumentException(
                    "Non puoi approvare/rifiutare una richiesta creata da te (separation of duties)."
            );
        }

        RequestStatus old = req.getStatus();
        req.setStatus(newStatus);

        logger.info("AUDIT: Admin/Operator '{}' changed request ID {} status {} -> {}",
                adminUserId, requestId, old, newStatus);
    }

    // ==========================================================
    // GDPR: Right to be forgotten (pulizia dati applicativi)
    // ==========================================================
    @Transactional
    public void deleteUserData(String userId) {
        requestRepository.deleteByUserId(userId);
        logger.info("AUDIT: User '{}' requested GDPR deletion -> removed all requests in PostgreSQL", userId);
    }
}

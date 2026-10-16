package com.example.requests_approvals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO per la creazione di una Request.
 * Implementa Input Validation lato server per garantire Data Integrity.
 */
public class CreateRequestForm {

    @NotBlank(message = "Tipo richiesta mancante.")
    @Pattern(
            regexp = "ACCESSO|PERMESSO|RIMBORSO|ALTRO",
            message = "Tipo richiesta non valido. Valori ammessi: ACCESSO, PERMESSO, RIMBORSO, ALTRO."
    )
    private String type;

    @NotBlank(message = "Descrizione mancante.")
    @Size(max = 500, message = "Descrizione troppo lunga (max 500 caratteri).")
    private String description;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

package com.example.requests_approvals.service;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.OAuth2Constants;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KeycloakService {

    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String clientSecret;

    // Metodo helper per ottenere la risorsa utente
    private UserResource getUserResource(String userId) {
        String serverUrl = issuerUri.substring(0, issuerUri.indexOf("/realms"));
        String realmName = issuerUri.substring(issuerUri.lastIndexOf("/") + 1);

        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realmName)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

        return keycloak.realm(realmName).users().get(userId);
    }

    // 1. Elimina utente
    public void deleteUserFromKeycloak(String userId) {
        getUserResource(userId).remove();
    }

    // 2. Cambia Password
    public void changePassword(String userId, String newPassword) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(newPassword);
        credential.setTemporary(false);

        getUserResource(userId).resetPassword(credential);
    }

    // 3. Cambia Email (NUOVO)
    public void updateEmail(String userId, String newEmail) {
        UserResource userResource = getUserResource(userId);

        // Recuperiamo i dati attuali e modifichiamo solo l'email
        UserRepresentation user = userResource.toRepresentation();
        user.setEmail(newEmail);

        // Inviamo l'aggiornamento
        userResource.update(user);
    }
}
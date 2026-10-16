package com.example.requests_approvals.security;

import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.AuthorizationResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service("pdp")
public class KeycloakPdpService {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    @Value("${keycloak.credentials.secret}")
    private String clientSecret;

    private final OAuth2AuthorizedClientService authorizedClientService;
    private AuthzClient authzClient;

    public KeycloakPdpService(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    private AuthzClient getAuthzClient() {
        if (authzClient == null) {
            Map<String, Object> credentials = new HashMap<>();
            credentials.put("secret", clientSecret);
            Configuration configuration = new Configuration(
                    authServerUrl, realm, clientId, credentials, null
            );
            authzClient = AuthzClient.create(configuration);
        }
        return authzClient;
    }

    public boolean check(Authentication authentication, String resourceName, String scope) {
        if (!(authentication instanceof OAuth2AuthenticationToken)) {
            // Se è un login classico o anonimo, restituisce false subito.
            return false;
        }

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

        try {
            // Recupera il client autorizzato per ottenere il token
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(),
                    oauthToken.getName());

            if (client == null || client.getAccessToken() == null) {
                return false;
            }

            String accessToken = client.getAccessToken().getTokenValue();

            // Preparazione della Domanda
            AuthorizationRequest request = new AuthorizationRequest();

            request.addPermission(resourceName, scope);

            AuthorizationResponse response = getAuthzClient().authorization(accessToken).authorize(request);
            return response.getToken() != null;

        } catch (Exception e) {
            System.out.println("❌ ERRORE PDP KEYCLOAK: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * METODO DI COMPATIBILITÀ (OVERLOAD):
     * Se nel codice chiami check senza specificare lo scope,
     * questo metodo imposta automaticamente "view" come default.
     * Utile per le @PreAuthorize già scritte.
     */
    //public boolean check(Authentication authentication, String resourceName) {
    //return check(authentication, resourceName, "view");
//}
}
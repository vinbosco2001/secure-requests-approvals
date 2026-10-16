package com.example.requests_approvals.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Fondamentale per @PreAuthorize; le decisioni principali ora passano dal bean @pdp
public class SecurityConfig {

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                // Mitigazione "Multiple X-Frame-Options": centralizziamo la gestione dell'header
                // anti-clickjacking (X-Frame-Options) su Apache Reverse Proxy.
                // Disabilitiamo l'invio dell'header da Spring Security per evitare duplicazioni.
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        // Qualsiasi richiesta richiede auth, ma i permessi veri li gestisce il Controller
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(grantedAuthoritiesMapper()))
                        .successHandler(myAuthenticationSuccessHandler())
                )
                .sessionManagement(session -> session
                        .sessionFixation().migrateSession()
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler())
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }

    // Handler per il Redirect "Intelligente" (Attribute-Based)
    // Funziona ancora perché legge gli ATTRIBUTI dell'utente (OidcUser), non i RUOLI (Authorities)
    @Bean
    public AuthenticationSuccessHandler myAuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            String redirectUrl = "/user";
            if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
                Object landingPageObj = oidcUser.getAttribute("landing_page");

                if (landingPageObj != null) {
                    if (landingPageObj instanceof String) {
                        redirectUrl = (String) landingPageObj;
                    } else if (landingPageObj instanceof List) {
                        List<?> list = (List<?>) landingPageObj;
                        if (!list.isEmpty() && list.get(0) instanceof String) {
                            redirectUrl = (String) list.get(0);
                        }
                    }
                }
            }
            response.sendRedirect(redirectUrl);
        };
    }

    /**
 * Mappa i Realm Roles di Keycloak (realm_access.roles) nel formato atteso da Spring Security (ROLE_*).
 * In questa versione i ruoli NON sono più il meccanismo principale di autorizzazione:
 * le decisioni applicative passano a Keycloak Authorization Services tramite KeycloakPdpService.
 * Manteniamo comunque il mapping come supporto a UI/diagnostica/compatibilità.
 */
@Bean
public GrantedAuthoritiesMapper grantedAuthoritiesMapper() {
    return (authorities) -> {
        // Manteniamo eventuali authorities già presenti
        Set<GrantedAuthority> mapped = authorities.stream().collect(Collectors.toSet());

        authorities.forEach(auth -> {
            if (auth instanceof org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority oidcAuth) {
                Map<String, Object> claims = oidcAuth.getIdToken().getClaims();
                Object realmAccessObj = claims.get("realm_access");
                if (realmAccessObj instanceof Map<?, ?> realmAccess) {
                    Object rolesObj = realmAccess.get("roles");
                    if (rolesObj instanceof Iterable<?> roles) {
                        for (Object r : roles) {
                            if (r instanceof String role) {
                                mapped.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                            }
                        }
                    }
                }
            }
        });
        return mapped;
    };
}

    // ... Bean CORS e Logout (Invariati) ...
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("https://requests.local", "http://localhost:8081"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler successHandler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        successHandler.setPostLogoutRedirectUri("{baseUrl}");
        return successHandler;
    }
}
package com.example.requests_approvals;

import com.example.requests_approvals.dto.CreateRequestForm;
import com.example.requests_approvals.model.RequestStatus;
import com.example.requests_approvals.security.KeycloakPdpService;
import com.example.requests_approvals.service.KeycloakService;
import com.example.requests_approvals.service.RequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class RequestController {

    private static final String RES_USER_AREA = "requests-user-area";
    private static final String RES_PROFILE = "requests-profile";
    private static final String RES_REQUEST = "request";
    private static final String RES_ADMIN_AREA = "requests-admin-area";

    private final RequestService requestService;
    private final KeycloakService keycloakService;
    private final KeycloakPdpService pdpService;

    public RequestController(RequestService requestService,
                             KeycloakService keycloakService,
                             KeycloakPdpService pdpService) {
        this.requestService = requestService;
        this.keycloakService = keycloakService;
        this.pdpService = pdpService;
    }

    @GetMapping("/")
    public String index() { return "redirect:/user"; }

    // =================================================================================
    //  AREA USER (autorizzazione delegata a Keycloak Authorization Services)
    // =================================================================================

    @PreAuthorize("@pdp.check(authentication, 'requests-user-area', 'view')")
    @GetMapping("/user")
    public String userArea(@AuthenticationPrincipal OidcUser principal,
                           Authentication authentication,
                           Model model,
                           @RequestParam(required = false) String message,
                           @RequestParam(required = false) String error) {

        String userId = principal.getPreferredUsername();
        model.addAttribute("username", userId);
        model.addAttribute("myRequests", requestService.getRequestsByUser(userId));
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        model.addAttribute("canViewAdminArea", pdpService.check(authentication, RES_ADMIN_AREA, "view"));
        return "user";
    }

    @PreAuthorize("@pdp.check(authentication, 'request', 'create')")
    @PostMapping("/user/request")
    public String createRequest(@AuthenticationPrincipal OidcUser principal,
                                @Valid @ModelAttribute CreateRequestForm form,
                                BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            String msg = bindingResult.getAllErrors().get(0).getDefaultMessage();
            return "redirect:/user?error=" + msg;
        }

        try {
            requestService.createRequest(
                    principal.getPreferredUsername(),
                    form.getType(),
                    form.getDescription()
            );
            return "redirect:/user?message=Richiesta inviata correttamente!";
        } catch (Exception e) {
            return "redirect:/user?error=" + e.getMessage();
        }
    }

    // =================================================================================
    //  GESTIONE PROFILO
    // =================================================================================

    @PreAuthorize("@pdp.check(authentication, 'requests-profile', 'view')")
    @GetMapping("/user/profile")
    public String userProfile(@AuthenticationPrincipal OidcUser principal, Model model,
                              @RequestParam(required = false) String message,
                              @RequestParam(required = false) String error) {
        model.addAttribute("username", principal.getPreferredUsername());
        model.addAttribute("email", principal.getEmail());
        model.addAttribute("fullName", principal.getFullName());
        model.addAttribute("userId", principal.getSubject());
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        return "profile";
    }

    @PreAuthorize("@pdp.check(authentication, 'requests-profile', 'password-change')")
    @GetMapping("/user/profile/password")
    public String showChangePasswordPage() { return "password_form"; }

    @PreAuthorize("@pdp.check(authentication, 'requests-profile', 'password-change')")
    @PostMapping("/user/profile/password")
    public String processChangePassword(@AuthenticationPrincipal OidcUser principal,
                                        @RequestParam String password,
                                        @RequestParam String confirmPassword,
                                        Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Le password non coincidono!");
            return "password_form";
        }
        try {
            keycloakService.changePassword(principal.getSubject(), password);
            return "redirect:/user/profile?message=Password aggiornata!";
        } catch (Exception e) {
            model.addAttribute("error", "Errore: " + e.getMessage());
            return "password_form";
        }
    }

    @PreAuthorize("@pdp.check(authentication, 'requests-profile', 'email-change')")
    @GetMapping("/user/profile/email")
    public String showChangeEmailPage(@AuthenticationPrincipal OidcUser principal, Model model) {
        model.addAttribute("currentEmail", principal.getEmail());
        return "email_form";
    }

    @PreAuthorize("@pdp.check(authentication, 'requests-profile', 'email-change')")
    @PostMapping("/user/profile/email")
    public String processChangeEmail(@AuthenticationPrincipal OidcUser principal,
                                     @RequestParam String newEmail, Model model) {
        try {
            keycloakService.updateEmail(principal.getSubject(), newEmail);
            return "redirect:/user/profile?message=Email aggiornata! Al prossimo login vedrai la modifica.";
        } catch (Exception e) {
            model.addAttribute("error", "Errore cambio email: " + e.getMessage());
            model.addAttribute("currentEmail", principal.getEmail());
            return "email_form";
        }
    }

    // ==========================================================
    // GDPR: Orchestrazione cancellazione completa (DB + Keycloak)
    // ==========================================================
    @PreAuthorize("@pdp.check(authentication, 'requests-profile', 'delete-account')")
    @PostMapping("/user/profile/delete")
    public String deleteFullAccount(@AuthenticationPrincipal OidcUser principal, HttpServletRequest request) {
        try {
            requestService.deleteUserData(principal.getPreferredUsername());
            keycloakService.deleteUserFromKeycloak(principal.getSubject());
            request.logout();
            return "redirect:/";
        } catch (Exception e) {
            return "redirect:/user/profile?error=Impossibile eliminare account: " + e.getMessage();
        }
    }

    // =================================================================================
    //  AREA ADMIN / OPERATOR
    // =================================================================================

    @PreAuthorize("@pdp.check(authentication, 'requests-admin-area', 'view')")
    @GetMapping("/admin")
    public String adminArea(Model model,
                            @RequestParam(required = false) String message,
                            @RequestParam(required = false) String error) {
        model.addAttribute("requests", requestService.getAllRequests());
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        return "admin";
    }

    @PostMapping("/admin/status/{id}")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam String status,
                               @AuthenticationPrincipal OidcUser principal,
                               Authentication authentication) {
        try {
            RequestStatus newStatus = RequestStatus.valueOf(status);
            String requiredScope = switch (newStatus) {
                case APPROVED -> "approve";
                case REJECTED -> "reject";
                default -> "view";
            };

            if (!pdpService.check(authentication, RES_REQUEST, requiredScope)) {
                throw new AccessDeniedException("Permesso negato da Keycloak Authorization Services.");
            }

            String adminUser = principal.getPreferredUsername();
            requestService.updateStatus(id, newStatus, adminUser);
            return "redirect:/admin?message=Stato aggiornato!";
        } catch (AccessDeniedException e) {
            return "redirect:/admin?error=" + e.getMessage();
        } catch (Exception e) {
            return "redirect:/admin?error=" + e.getMessage();
        }
    }
}

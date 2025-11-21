package com.example.saml.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class AuthController implements AuthenticationEntryPoint {

  private final AuthenticationManager authenticationManager;
  private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
  private final SecurityContextRepository securityContextRepository;

  public AuthController(AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository) {
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
  }

  @PostMapping("/auth/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    try {
      Authentication authentication = authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(request.username(), request.password()));
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
      servletRequest.getSession(true);
      securityContextRepository.saveContext(context, servletRequest, servletResponse);
      return ResponseEntity.ok(Map.of("status", "ok"));
    } catch (AuthenticationException ex) {
      return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).body("Bad credentials");
    }
  }

  @PostMapping("/auth/logout")
  public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null) {
      logoutHandler.logout(request, response, authentication);
    }
    return ResponseEntity.ok(Map.of("status", "logged_out"));
  }

  @GetMapping("/me")
  public ResponseEntity<?> me(Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).build();
    }
    Object principal = authentication.getPrincipal();
    String name = principal instanceof UserDetails user ? user.getUsername() : authentication.getName();
    Map<String, ?> attributes = null;
    if (principal instanceof org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal saml) {
      attributes = saml.getAttributes();
    } else if (authentication.getDetails() instanceof Map<?, ?> details) {
      attributes = (Map<String, ?>) details;
    }
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("name", name);
    body.put("authorities", authentication.getAuthorities().stream().map(Object::toString).toList());
    if (attributes != null) {
      body.put("attributes", attributes);
    }
    return ResponseEntity.ok(body);
  }

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException {
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
  }

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {
  }
}

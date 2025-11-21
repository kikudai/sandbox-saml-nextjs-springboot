package com.example.saml.config;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Value("${app.frontend-base-url:http://localhost:3000}")
  private String frontendBaseUrl;

  private final ObjectProvider<RelyingPartyRegistrationRepository> relyingPartyRegistrationRepositoryProvider;

  public SecurityConfig(ObjectProvider<RelyingPartyRegistrationRepository> relyingPartyRegistrationRepositoryProvider) {
    this.relyingPartyRegistrationRepositoryProvider = relyingPartyRegistrationRepositoryProvider;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    RelyingPartyRegistrationRepository samlRepo = relyingPartyRegistrationRepositoryProvider.getIfAvailable();
    boolean samlEnabled = samlRepo != null;

    http
        .csrf(csrf -> csrf.ignoringRequestMatchers(
            new AntPathRequestMatcher("/api/**"),
            new AntPathRequestMatcher("/saml2/**"),
            new AntPathRequestMatcher("/login/saml2/**")))
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
            .requestMatchers("/saml2/**", "/login/saml2/**").permitAll()
            .anyRequest().authenticated())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, authEx) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
        .logout(logout -> logout
            .logoutUrl("/api/auth/logout")
            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_OK)))
        .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository()))
        .sessionManagement(session -> session
            .maximumSessions(1)
            .maxSessionsPreventsLogin(false));

    if (samlEnabled) {
      http.saml2Login(saml2 -> saml2
          .relyingPartyRegistrationRepository(samlRepo)
          .defaultSuccessUrl(frontendBaseUrl, true));
    } else {
      http.saml2Login(AbstractHttpConfigurer::disable);
    }

    return http.build();
  }

  @Bean
  public UserDetailsService userDetailsService(PasswordEncoder encoder) {
    UserDetails user = User.builder()
        .username("user")
        .password(encoder.encode("password"))
        .roles("USER")
        .build();
    UserDetails admin = User.builder()
        .username("admin")
        .password(encoder.encode("adminpass"))
        .roles("ADMIN", "USER")
        .build();
    return new InMemoryUserDetailsManager(List.of(user, admin));
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  @ConditionalOnProperty(name = "app.saml.enabled", havingValue = "true")
  public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository(
      @Value("${app.saml.metadata-uri}") String metadataUri,
      @Value("${app.saml.entity-id}") String entityId) {
    RelyingPartyRegistration registration = RelyingPartyRegistrations
        .fromMetadataLocation(metadataUri)
        .registrationId("entra")
        .entityId(entityId)
        .build();
    return new InMemoryRelyingPartyRegistrationRepository(registration);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://127.0.0.1:3000"));
    configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}

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
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.core.io.ClassPathResource;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  @Value("${app.frontend-base-url:http://localhost:3000}")
  private String frontendBaseUrl;

  @Value("${app.saml.enabled:false}")
  private boolean samlEnabledProp;

  private final ObjectProvider<RelyingPartyRegistrationRepository> relyingPartyRegistrationRepositoryProvider;

  public SecurityConfig(ObjectProvider<RelyingPartyRegistrationRepository> relyingPartyRegistrationRepositoryProvider) {
    this.relyingPartyRegistrationRepositoryProvider = relyingPartyRegistrationRepositoryProvider;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    RelyingPartyRegistrationRepository samlRepo = relyingPartyRegistrationRepositoryProvider.getIfAvailable();
    boolean samlEnabled = samlRepo != null;
    log.info("Security filter chain init: app.saml.enabled={}, RelyingPartyRegistrationRepository present={}",
        samlEnabledProp, samlEnabled);

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
          .defaultSuccessUrl(frontendBaseUrl, true)
          .failureHandler((request, response, exception) -> {
            log.error("SAML authentication failed", exception);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
          }));
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
  public WebSecurityCustomizer webSecurityCustomizer() {
    return web -> web.ignoring().requestMatchers("/saml2/service-provider-metadata/**");
  }

  @Bean
  @ConditionalOnProperty(name = "app.saml.enabled", havingValue = "true")
  public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository(
      @Value("${app.saml.metadata-uri}") String metadataUri,
      @Value("${app.saml.entity-id}") String entityId) {
    log.info("Creating RelyingPartyRegistrationRepository (app.saml.enabled=true) metadataUri={}, entityId={}",
        metadataUri, entityId);
    
    RelyingPartyRegistration.Builder builder = RelyingPartyRegistrations
        .fromMetadataLocation(metadataUri)
        .registrationId("entra")
        .entityId(entityId);
    
    // SP側の署名鍵を明示的に設定
    try {
      Saml2X509Credential signingCredential = loadSigningCredential();
      if (signingCredential != null) {
        builder.signingX509Credentials(credentials -> credentials.add(signingCredential));
        log.info("SP signing credential loaded from classpath:saml/sp-signing.*");
      }
    } catch (Exception e) {
      log.warn("Failed to load SP signing credential from classpath:saml/sp-signing.*: {}", e.getMessage());
      log.warn("SP metadata may not include signing certificate. SAML requests may not be signed.");
    }
    
    RelyingPartyRegistration registration = builder.build();
    log.info("Created RelyingPartyRegistrationRepository for registrationId=entra, entityId={}", entityId);
    return new InMemoryRelyingPartyRegistrationRepository(registration);
  }
  
  private Saml2X509Credential loadSigningCredential() throws Exception {
    try {
      // 証明書の読み込み
      ClassPathResource certResource = new ClassPathResource("saml/sp-signing.crt");
      if (!certResource.exists()) {
        log.debug("Certificate file not found: classpath:saml/sp-signing.crt");
        return null;
      }
      
      CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
      X509Certificate certificate;
      try (InputStream certStream = certResource.getInputStream()) {
        certificate = (X509Certificate) certFactory.generateCertificate(certStream);
      }
      
      // 秘密鍵の読み込み
      ClassPathResource keyResource = new ClassPathResource("saml/sp-signing.key");
      if (!keyResource.exists()) {
        log.debug("Private key file not found: classpath:saml/sp-signing.key");
        return null;
      }
      
      PrivateKey privateKey;
      try (InputStream keyStream = keyResource.getInputStream()) {
        String keyContent = new String(keyStream.readAllBytes());
        // PEM形式の秘密鍵をパース（PKCS#8形式を想定）
        String privateKeyPEM = keyContent
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        privateKey = keyFactory.generatePrivate(keySpec);
      }
      
      return new Saml2X509Credential(privateKey, certificate, Saml2X509Credential.Saml2X509CredentialType.SIGNING);
    } catch (Exception e) {
      log.error("Error loading signing credential", e);
      throw e;
    }
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    String frontendOrigin = frontendBaseUrl != null ? frontendBaseUrl.replaceAll("/+$", "") : "http://localhost:3000";

    CorsConfiguration defaultConfig = new CorsConfiguration();
    // Restrict CORS to the known frontend origin
    defaultConfig.setAllowedOriginPatterns(List.of(frontendOrigin));
    defaultConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    defaultConfig.setAllowedHeaders(List.of("*"));
    defaultConfig.setAllowCredentials(true);

    CorsConfiguration samlAcsConfig = new CorsConfiguration();
    // Allow the IdP (Entra) to POST the SAML Response to ACS, and the frontend origin.
    // Some IdP responses include an Origin that may not match the tenant subdomain exactly,
    // so we allow all for ACS endpoints to avoid false CORS rejections.
    samlAcsConfig.setAllowedOriginPatterns(List.of("*"));
    samlAcsConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    samlAcsConfig.setAllowedHeaders(List.of("*"));
    samlAcsConfig.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    // Apply ACS-specific CORS first (more specific path)
    source.registerCorsConfiguration("/login/saml2/**", samlAcsConfig);
    source.registerCorsConfiguration("/saml2/**", samlAcsConfig);
    source.registerCorsConfiguration("/**", defaultConfig);
    return source;
  }
}

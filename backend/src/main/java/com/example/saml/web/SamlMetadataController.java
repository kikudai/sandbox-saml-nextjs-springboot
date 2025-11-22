package com.example.saml.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.saml2.provider.service.metadata.OpenSamlMetadataResolver;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@ConditionalOnBean(RelyingPartyRegistrationRepository.class)
public class SamlMetadataController {

  private static final Logger log = LoggerFactory.getLogger(SamlMetadataController.class);

  private final RelyingPartyRegistrationRepository repository;
  private final OpenSamlMetadataResolver metadataResolver = new OpenSamlMetadataResolver();

  public SamlMetadataController(RelyingPartyRegistrationRepository repository) {
    log.info("SamlMetadataController initialized with repository={}", repository.getClass().getSimpleName());
    this.repository = repository;
  }

  @GetMapping(path = "/saml2/service-provider-metadata/{registrationId}", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> metadata(@PathVariable("registrationId") String registrationId) {
    RelyingPartyRegistration registration = repository.findByRegistrationId(registrationId);
    if (registration == null) {
      log.warn("Requested metadata for unknown registrationId={}", registrationId);
      return ResponseEntity.notFound().build();
    }
    String metadata = metadataResolver.resolve(registration);
    log.debug("Serving metadata for registrationId={}", registrationId);
    return ResponseEntity.ok(metadata);
  }
}

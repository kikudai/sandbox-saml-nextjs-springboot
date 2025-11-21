package com.example.saml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyAutoConfiguration;

@SpringBootApplication(exclude = Saml2RelyingPartyAutoConfiguration.class)
public class SamlBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(SamlBackendApplication.class, args);
  }
}

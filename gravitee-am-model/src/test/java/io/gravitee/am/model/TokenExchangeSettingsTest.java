/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.common.oauth2.TokenType.ACCESS_TOKEN;
import static io.gravitee.am.common.oauth2.TokenType.ID_TOKEN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenExchangeSettingsTest {

    @Test
    void isValid_disabledIsAlwaysValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(false);
        settings.setAllowedSubjectTokenTypes(Collections.emptyList());
        settings.setAllowedRequestedTokenTypes(Collections.emptyList());
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_disabledWithNullListsIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(false);
        settings.setAllowedSubjectTokenTypes(null);
        settings.setAllowedRequestedTokenTypes(null);
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_enabledWithDefaultsIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_enabledWithEmptySubjectTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(Collections.emptyList());
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithNullSubjectTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(null);
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithEmptyRequestedTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedRequestedTokenTypes(Collections.emptyList());
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithNullRequestedTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedRequestedTokenTypes(null);
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithNeitherImpersonationNorDelegationIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(false);
        settings.setAllowDelegation(false);
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithDelegationAndEmptyActorTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(false);
        settings.setAllowDelegation(true);
        settings.setAllowedActorTokenTypes(Collections.emptyList());
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithDelegationAndNullActorTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(false);
        settings.setAllowDelegation(true);
        settings.setAllowedActorTokenTypes(null);
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_enabledWithDelegationAndPopulatedActorTokenTypesIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(false);
        settings.setAllowDelegation(true);
        settings.setAllowedActorTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN)));
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_enabledWithImpersonationOnlyAndAllListsPopulatedIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(true);
        settings.setAllowDelegation(false);
        settings.setAllowedSubjectTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN)));
        settings.setAllowedRequestedTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN, ID_TOKEN)));
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_enabledWithImpersonationAndEmptyActorTokenTypesIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(true);
        settings.setAllowDelegation(false);
        settings.setAllowedActorTokenTypes(Collections.emptyList());
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_enabledWithBothModesAndAllListsPopulatedIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(true);
        settings.setAllowDelegation(true);
        settings.setAllowedSubjectTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN)));
        settings.setAllowedRequestedTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN, ID_TOKEN)));
        settings.setAllowedActorTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN)));
        assertTrue(settings.isValid());
    }

    // --- Trusted Issuers validation tests ---

    @Test
    void isValid_nullTrustedIssuersIsValid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(null);
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_emptyTrustedIssuersIsValid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(Collections.emptyList());
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_trustedIssuerWithJwksUrlIsValid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json")));
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_trustedIssuerWithPemIsValid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(pemIssuer("https://another-idp.example.com", "-----BEGIN CERTIFICATE-----\nMIIB...\n-----END CERTIFICATE-----")));
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_trustedIssuerWithScopeMappingsIsValid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setScopeMappings(Map.of("ext:read", "domain:read", "ext:write", "domain:write"));
        settings.setTrustedIssuers(List.of(issuer));
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_trustedIssuerWithBlankIssuerIsInvalid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(jwksIssuer("", "https://example.com/jwks")));
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_trustedIssuerWithNullIssuerIsInvalid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(jwksIssuer(null, "https://example.com/jwks")));
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_trustedIssuerWithInvalidMethodIsInvalid() {
        var settings = enabledSettings();
        var ti = new TrustedIssuer();
        ti.setIssuer("https://example.com");
        ti.setKeyResolutionMethod("INVALID");
        settings.setTrustedIssuers(List.of(ti));
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_jwksIssuerWithBlankUriIsInvalid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(jwksIssuer("https://example.com", "")));
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_jwksIssuerWithInvalidUriIsInvalid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(jwksIssuer("https://example.com", "not a valid url")));
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_pemIssuerWithBlankCertificateIsInvalid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(pemIssuer("https://example.com", "")));
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_duplicateIssuerUrlsIsInvalid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(
                jwksIssuer("https://same-issuer.example.com", "https://same-issuer.example.com/jwks"),
                jwksIssuer("https://same-issuer.example.com", "https://same-issuer.example.com/jwks2")
        ));
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_multipleDifferentIssuersIsValid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(
                jwksIssuer("https://idp1.example.com", "https://idp1.example.com/jwks"),
                pemIssuer("https://idp2.example.com", "-----BEGIN CERTIFICATE-----\nMIIB...\n-----END CERTIFICATE-----")
        ));
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_disabledWithInvalidTrustedIssuersIsStillValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(false);
        settings.setTrustedIssuers(List.of(jwksIssuer("", "")));
        assertTrue(settings.isValid());
    }

    // --- User Binding validation tests ---

    @Test
    void isValid_userBindingEnabledWithCriteriaIsValid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(true);
        issuer.setUserBindingCriteria(List.of(criterion("email", "email")));
        settings.setTrustedIssuers(List.of(issuer));
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_userBindingEnabledWithMultipleCriteriaIsValid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(true);
        issuer.setUserBindingCriteria(List.of(
                criterion("email", "{#token['email']}"),
                criterion("username", "preferred_username")));
        settings.setTrustedIssuers(List.of(issuer));
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_userBindingEnabledWithNullCriteriaIsInvalid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(true);
        issuer.setUserBindingCriteria(null);
        settings.setTrustedIssuers(List.of(issuer));
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_userBindingEnabledWithEmptyCriteriaIsInvalid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(true);
        issuer.setUserBindingCriteria(Collections.emptyList());
        settings.setTrustedIssuers(List.of(issuer));
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_userBindingEnabledWithBlankAttributeIsInvalid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(true);
        issuer.setUserBindingCriteria(List.of(criterion("", "email")));
        settings.setTrustedIssuers(List.of(issuer));
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_userBindingEnabledWithBlankExpressionIsInvalid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(true);
        issuer.setUserBindingCriteria(List.of(criterion("email", "  ")));
        settings.setTrustedIssuers(List.of(issuer));
        assertFalse(settings.isValid());
    }

    @Test
    void isValid_userBindingDisabledWithNoCriteriaIsValid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(false);
        issuer.setUserBindingCriteria(null);
        settings.setTrustedIssuers(List.of(issuer));
        assertTrue(settings.isValid());
    }

    private static TokenExchangeSettings enabledSettings() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        return settings;
    }

    private static TrustedIssuer jwksIssuer(String issuer, String jwksUri) {
        var ti = new TrustedIssuer();
        ti.setIssuer(issuer);
        ti.setKeyResolutionMethod(TrustedIssuer.KEY_RESOLUTION_JWKS_URL);
        ti.setJwksUri(jwksUri);
        return ti;
    }

    private static TrustedIssuer pemIssuer(String issuer, String certificate) {
        var ti = new TrustedIssuer();
        ti.setIssuer(issuer);
        ti.setKeyResolutionMethod(TrustedIssuer.KEY_RESOLUTION_PEM);
        ti.setCertificate(certificate);
        return ti;
    }

    private static UserBindingCriterion criterion(String attribute, String expression) {
        var c = new UserBindingCriterion();
        c.setAttribute(attribute);
        c.setExpression(expression);
        return c;
    }
}

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
package io.gravitee.am.service.validators;

import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.service.exception.InvalidDomainException;
import io.gravitee.am.service.validators.tokenexchange.TokenExchangeSettingsValidator;
import io.gravitee.am.service.validators.tokenexchange.TokenExchangeSettingsValidatorImpl;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.common.oauth2.TokenType.ACCESS_TOKEN;
import static io.gravitee.am.common.oauth2.TokenType.ID_TOKEN;

class TokenExchangeSettingsValidatorTest {

    private TokenExchangeSettingsValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TokenExchangeSettingsValidatorImpl(5);
    }

    // --- Disabled / null settings ---

    @Test
    void validate_nullSettingsCompletes() {
        validator.validate(null).test().assertComplete();
    }

    @Test
    void validate_disabledIsAlwaysValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(false);
        settings.setAllowedSubjectTokenTypes(Collections.emptyList());
        settings.setAllowedRequestedTokenTypes(Collections.emptyList());
        validator.validate(settings).test().assertComplete();
    }

    @Test
    void validate_disabledWithNullListsIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(false);
        settings.setAllowedSubjectTokenTypes(null);
        settings.setAllowedRequestedTokenTypes(null);
        validator.validate(settings).test().assertComplete();
    }

    @Test
    void validate_enabledWithDefaultsIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        validator.validate(settings).test().assertComplete();
    }

    // --- Token type list validation ---

    @Test
    void validate_enabledWithEmptySubjectTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(Collections.emptyList());
        assertError(settings, "Allowed subject token types must not be empty");
    }

    @Test
    void validate_enabledWithNullSubjectTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(null);
        assertError(settings, "Allowed subject token types must not be empty");
    }

    @Test
    void validate_enabledWithEmptyRequestedTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedRequestedTokenTypes(Collections.emptyList());
        assertError(settings, "Allowed requested token types must not be empty");
    }

    @Test
    void validate_enabledWithNullRequestedTokenTypesUsesDefaults() {
        // getAllowedRequestedTokenTypes() is null-safe and returns defaults when null
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedRequestedTokenTypes(null);
        validator.validate(settings).test().assertComplete();
    }

    @Test
    void validate_enabledWithNeitherImpersonationNorDelegationIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(false);
        settings.setAllowDelegation(false);
        assertError(settings, "At least one of impersonation or delegation must be enabled");
    }

    // --- Delegation validation ---

    @Test
    void validate_enabledWithDelegationAndEmptyActorTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(false);
        settings.setAllowDelegation(true);
        settings.setAllowedActorTokenTypes(Collections.emptyList());
        assertError(settings, "Allowed actor token types must not be empty when delegation is enabled");
    }

    @Test
    void validate_enabledWithDelegationAndNullActorTokenTypesIsInvalid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(false);
        settings.setAllowDelegation(true);
        settings.setAllowedActorTokenTypes(null);
        assertError(settings, "Allowed actor token types must not be empty when delegation is enabled");
    }

    @Test
    void validate_enabledWithDelegationAndPopulatedActorTokenTypesIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(false);
        settings.setAllowDelegation(true);
        settings.setAllowedActorTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN)));
        validator.validate(settings).test().assertComplete();
    }

    @Test
    void validate_enabledWithImpersonationOnlyAndAllListsPopulatedIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(true);
        settings.setAllowDelegation(false);
        settings.setAllowedSubjectTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN)));
        settings.setAllowedRequestedTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN, ID_TOKEN)));
        validator.validate(settings).test().assertComplete();
    }

    @Test
    void validate_enabledWithImpersonationAndEmptyActorTokenTypesIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(true);
        settings.setAllowDelegation(false);
        settings.setAllowedActorTokenTypes(Collections.emptyList());
        validator.validate(settings).test().assertComplete();
    }

    @Test
    void validate_enabledWithBothModesAndAllListsPopulatedIsValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowImpersonation(true);
        settings.setAllowDelegation(true);
        settings.setAllowedSubjectTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN)));
        settings.setAllowedRequestedTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN, ID_TOKEN)));
        settings.setAllowedActorTokenTypes(new ArrayList<>(List.of(ACCESS_TOKEN)));
        validator.validate(settings).test().assertComplete();
    }

    // --- Trusted Issuers ---

    @Test
    void validate_nullTrustedIssuersIsValid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(null);
        validator.validate(settings).test().assertComplete();
    }

    @Test
    void validate_emptyTrustedIssuersIsValid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(Collections.emptyList());
        validator.validate(settings).test().assertComplete();
    }

    @Test
    void validate_trustedIssuerWithJwksUrlIsValid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json")));
        validator.validate(settings).test().assertComplete();
    }

    @Test
    void validate_trustedIssuerWithScopeMappingsIsValid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setScopeMappings(Map.of("ext:read", "domain:read", "ext:write", "domain:write"));
        settings.setTrustedIssuers(List.of(issuer));
        validator.validate(settings).test().assertComplete();
    }

    @Test
    void validate_trustedIssuerWithBlankIssuerIsInvalid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(jwksIssuer("", "https://example.com/jwks")));
        assertError(settings, "Trusted issuer URL must not be blank");
    }

    @Test
    void validate_trustedIssuerWithNullIssuerIsInvalid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(jwksIssuer(null, "https://example.com/jwks")));
        assertError(settings, "Trusted issuer URL must not be blank");
    }

    @Test
    void validate_trustedIssuerWithNullMethodIsInvalid() {
        var settings = enabledSettings();
        var ti = new TrustedIssuer();
        ti.setIssuer("https://example.com");
        ti.setKeyResolutionMethod(null);
        settings.setTrustedIssuers(List.of(ti));
        assertError(settings, "Key resolution method must not be null");
    }

    @Test
    void validate_jwksIssuerWithBlankUriIsInvalid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(jwksIssuer("https://example.com", "")));
        assertError(settings, "JWKS URI must not be blank");
    }

    @Test
    void validate_jwksIssuerWithInvalidUriIsInvalid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(jwksIssuer("https://example.com", "not a valid url")));
        assertError(settings, "Invalid JWKS URI for trusted issuer");
    }

    @Test
    void validate_pemIssuerWithBlankCertificateIsInvalid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(pemIssuer("https://example.com", "")));
        assertError(settings, "PEM certificate must not be blank");
    }

    @Test
    void validate_pemIssuerWithInvalidCertificateIsInvalid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(pemIssuer("https://example.com", "not-a-valid-pem-certificate")));
        assertError(settings, "Invalid PEM certificate for trusted issuer");
    }

    @Test
    void validate_duplicateIssuerUrlsIsInvalid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(
                jwksIssuer("https://same-issuer.example.com", "https://same-issuer.example.com/jwks"),
                jwksIssuer("https://same-issuer.example.com", "https://same-issuer.example.com/jwks2")
        ));
        assertError(settings, "Duplicate trusted issuer URL:");
    }

    @Test
    void validate_exceedingMaxTrustedIssuerCountIsInvalid() {
        var settings = enabledSettings();
        var issuers = new ArrayList<TrustedIssuer>();
        for (int i = 0; i < 6; i++) {
            issuers.add(jwksIssuer("https://issuer-" + i + ".example.com", "https://issuer-" + i + ".example.com/jwks"));
        }
        settings.setTrustedIssuers(issuers);
        assertError(settings, "Maximum number of trusted issuers exceeded");
    }

    @Test
    void validate_multipleDifferentIssuersIsValid() {
        var settings = enabledSettings();
        settings.setTrustedIssuers(List.of(
                jwksIssuer("https://idp1.example.com", "https://idp1.example.com/jwks"),
                jwksIssuer("https://idp2.example.com", "https://idp2.example.com/jwks")
        ));
        validator.validate(settings).test().assertComplete();
    }

    @Test
    void validate_disabledWithInvalidTrustedIssuersIsStillValid() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(false);
        settings.setTrustedIssuers(List.of(jwksIssuer("", "")));
        validator.validate(settings).test().assertComplete();
    }

    // --- User Binding ---

    @Test
    void validate_userBindingEnabledWithCriteriaIsValid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(true);
        issuer.setUserBindingCriteria(List.of(criterion("email", "email")));
        settings.setTrustedIssuers(List.of(issuer));
        validator.validate(settings).test().assertComplete();
    }

    @Test
    void validate_userBindingEnabledWithMultipleCriteriaIsValid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(true);
        issuer.setUserBindingCriteria(List.of(
                criterion("email", "{#token['email']}"),
                criterion("username", "preferred_username")));
        settings.setTrustedIssuers(List.of(issuer));
        validator.validate(settings).test().assertComplete();
    }

    @Test
    void validate_userBindingEnabledWithNullCriteriaIsInvalid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(true);
        issuer.setUserBindingCriteria(null);
        settings.setTrustedIssuers(List.of(issuer));
        assertError(settings, "User binding is enabled for trusted issuer");
    }

    @Test
    void validate_userBindingEnabledWithEmptyCriteriaIsInvalid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(true);
        issuer.setUserBindingCriteria(Collections.emptyList());
        settings.setTrustedIssuers(List.of(issuer));
        assertError(settings, "User binding is enabled for trusted issuer");
    }

    @Test
    void validate_userBindingEnabledWithBlankAttributeIsInvalid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(true);
        issuer.setUserBindingCriteria(List.of(criterion("", "email")));
        settings.setTrustedIssuers(List.of(issuer));
        assertError(settings, "must have non-empty attribute and expression");
    }

    @Test
    void validate_userBindingEnabledWithBlankExpressionIsInvalid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(true);
        issuer.setUserBindingCriteria(List.of(criterion("email", "  ")));
        settings.setTrustedIssuers(List.of(issuer));
        assertError(settings, "must have non-empty attribute and expression");
    }

    @Test
    void validate_userBindingDisabledWithNoCriteriaIsValid() {
        var settings = enabledSettings();
        var issuer = jwksIssuer("https://external-idp.example.com", "https://external-idp.example.com/.well-known/jwks.json");
        issuer.setUserBindingEnabled(false);
        issuer.setUserBindingCriteria(null);
        settings.setTrustedIssuers(List.of(issuer));
        validator.validate(settings).test().assertComplete();
    }

    // --- Helpers ---

    private void assertError(TokenExchangeSettings settings, String expectedMessage) {
        TestObserver<Void> observer = validator.validate(settings).test();
        observer.assertError(throwable ->
                throwable instanceof InvalidDomainException
                        && throwable.getMessage().contains(expectedMessage));
    }

    private static TokenExchangeSettings enabledSettings() {
        var settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        return settings;
    }

    private static TrustedIssuer jwksIssuer(String issuer, String jwksUri) {
        var ti = new TrustedIssuer();
        ti.setIssuer(issuer);
        ti.setKeyResolutionMethod(KeyResolutionMethod.JWKS_URL);
        ti.setJwksUri(jwksUri);
        return ti;
    }

    private static TrustedIssuer pemIssuer(String issuer, String certificate) {
        var ti = new TrustedIssuer();
        ti.setIssuer(issuer);
        ti.setKeyResolutionMethod(KeyResolutionMethod.PEM);
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

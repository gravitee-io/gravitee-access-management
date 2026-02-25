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
package io.gravitee.am.service.validators.tokenexchange;

import io.gravitee.am.certificate.api.X509CertUtils;
import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.service.exception.InvalidDomainException;
import io.reactivex.rxjava3.core.Completable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service-layer validator for {@link TokenExchangeSettings}.
 *
 * @author GraviteeSource Team
 */
@Component
public class TokenExchangeSettingsValidatorImpl implements TokenExchangeSettingsValidator {

    private final int trustedIssuersMaxCount;

    public TokenExchangeSettingsValidatorImpl(
            @Value("${domain.tokenExchange.trustedIssuers.maxCount:5}") int trustedIssuersMaxCount) {
        this.trustedIssuersMaxCount = trustedIssuersMaxCount;
    }

    @Override
    public Completable validate(TokenExchangeSettings settings) {
        if (settings == null || !settings.isEnabled()) {
            return Completable.complete();
        }

        if (!(settings.isAllowImpersonation() || settings.isAllowDelegation())) {
            return error("At least one of impersonation or delegation must be enabled");
        }
        if (settings.getAllowedSubjectTokenTypes() == null || settings.getAllowedSubjectTokenTypes().isEmpty()) {
            return error("Allowed subject token types must not be empty when token exchange is enabled");
        }
        if (settings.getAllowedRequestedTokenTypes() == null || settings.getAllowedRequestedTokenTypes().isEmpty()) {
            return error("Allowed requested token types must not be empty when token exchange is enabled");
        }
        if (settings.isAllowDelegation()
                && (settings.getAllowedActorTokenTypes() == null || settings.getAllowedActorTokenTypes().isEmpty())) {
            return error("Allowed actor token types must not be empty when delegation is enabled");
        }
        if (settings.isAllowDelegation()
                && (settings.getMaxDelegationDepth() < TokenExchangeSettings.MIN_MAX_DELEGATION_DEPTH
                    || settings.getMaxDelegationDepth() > TokenExchangeSettings.MAX_MAX_DELEGATION_DEPTH)) {
            return error("Max delegation depth must be between "
                    + TokenExchangeSettings.MIN_MAX_DELEGATION_DEPTH + " and " + TokenExchangeSettings.MAX_MAX_DELEGATION_DEPTH);
        }

        return validateTrustedIssuers(settings.getTrustedIssuers());
    }

    private Completable validateTrustedIssuers(List<TrustedIssuer> trustedIssuers) {
        if (trustedIssuers == null || trustedIssuers.isEmpty()) {
            return Completable.complete();
        }
        if (trustedIssuers.size() > trustedIssuersMaxCount) {
            return error("Maximum number of trusted issuers exceeded (max: " + trustedIssuersMaxCount + ")");
        }

        Set<String> seenIssuers = new HashSet<>();
        for (TrustedIssuer ti : trustedIssuers) {
            Completable result = validateSingleIssuer(ti, seenIssuers);
            if (result != null) {
                return result;
            }
        }
        return Completable.complete();
    }

    private Completable validateSingleIssuer(TrustedIssuer ti, Set<String> seenIssuers) {
        if (ti.getIssuer() == null || ti.getIssuer().isBlank()) {
            return error("Trusted issuer URL must not be blank");
        }
        if (!seenIssuers.add(ti.getIssuer())) {
            return error("Duplicate trusted issuer URL: " + ti.getIssuer());
        }

        KeyResolutionMethod method = ti.getKeyResolutionMethod();
        if (method == null) {
            return error("Key resolution method must not be null for trusted issuer: " + ti.getIssuer());
        }

        Completable keyResult = validateKeyMaterial(ti, method);
        if (keyResult != null) {
            return keyResult;
        }
        return validateUserBinding(ti);
    }

    private Completable validateKeyMaterial(TrustedIssuer ti, KeyResolutionMethod method) {
        return switch (method) {
            case JWKS_URL -> validateJwksUri(ti);
            case PEM -> validatePemCertificate(ti);
        };
    }

    private Completable validateJwksUri(TrustedIssuer ti) {
        if (ti.getJwksUri() == null || ti.getJwksUri().isBlank()) {
            return error("JWKS URI must not be blank for trusted issuer: " + ti.getIssuer());
        }
        try {
            URI.create(ti.getJwksUri()).toURL();
        } catch (Exception e) {
            return error("Invalid JWKS URI for trusted issuer " + ti.getIssuer() + ": " + ti.getJwksUri());
        }
        return null;
    }

    private Completable validatePemCertificate(TrustedIssuer ti) {
        if (ti.getCertificate() == null || ti.getCertificate().isBlank()) {
            return error("PEM certificate must not be blank for trusted issuer: " + ti.getIssuer());
        }
        if (X509CertUtils.parse(ti.getCertificate()) == null) {
            return error("Invalid PEM certificate for trusted issuer: " + ti.getIssuer());
        }
        return null;
    }

    private Completable validateUserBinding(TrustedIssuer ti) {
        if (!ti.isUserBindingEnabled()) {
            return null;
        }
        var criteria = ti.getUserBindingCriteria();
        if (criteria == null || criteria.isEmpty()) {
            return error("User binding is enabled for trusted issuer " + ti.getIssuer() + " but no criteria are defined");
        }
        for (UserBindingCriterion c : criteria) {
            if (c.getAttribute() == null || c.getAttribute().isBlank()
                    || c.getExpression() == null || c.getExpression().isBlank()) {
                return error("User binding criteria for trusted issuer " + ti.getIssuer()
                        + " must have non-empty attribute and expression");
            }
        }
        return null;
    }

    private static Completable error(String message) {
        return Completable.error(new InvalidDomainException(message));
    }
}

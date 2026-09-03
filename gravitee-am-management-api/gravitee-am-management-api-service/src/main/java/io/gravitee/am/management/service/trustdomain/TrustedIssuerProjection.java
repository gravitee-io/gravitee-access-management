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
package io.gravitee.am.management.service.trustdomain;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.service.TrustDomainService;
import io.gravitee.am.service.model.NewTrustDomain;
import io.gravitee.am.service.model.UpdateTrustDomain;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * Backs the deprecated inline trusted-issuer list of a security domain's token-exchange settings
 * with the trusted domains that vouch for an issuer, which are the only place external trust is
 * stored.
 */
@Component
public class TrustedIssuerProjection {

    private static final String CLEAR_ISSUER = "";

    private final TrustDomainService trustDomainService;

    public TrustedIssuerProjection(TrustDomainService trustDomainService) {
        this.trustDomainService = trustDomainService;
    }

    /**
     * Assembles the deprecated inline list onto the given security domain from its token-exchange
     * trusted domains. A trusted domain whose key material is an inline JWK set has no inline
     * representation and is reported without a key-resolution method.
     */
    public Single<Domain> project(Domain domain) {
        return tokenExchangeTrustDomains(domain.getId())
                .map(trustDomains -> {
                    if (trustDomains.isEmpty() && domain.getTokenExchangeSettings() == null) {
                        return domain;
                    }
                    if (domain.getTokenExchangeSettings() == null) {
                        domain.setTokenExchangeSettings(new TokenExchangeSettings());
                    }
                    domain.getTokenExchangeSettings().setTrustedIssuers(
                            trustDomains.isEmpty() ? null : trustDomains.stream().map(TrustedIssuerProjection::asTrustedIssuer).toList());
                    return domain;
                });
    }

    /**
     * Translates a written inline list into trusted-domain creates, updates and deletes. The list
     * replaces what the security domain trusts, so an issuer absent from it is deleted. A null
     * list means the deprecated field was not written and leaves the trusted domains alone.
     */
    public Completable apply(Domain domain, List<TrustedIssuer> written, User principal) {
        if (written == null) {
            return Completable.complete();
        }
        return tokenExchangeTrustDomains(domain.getId())
                .flatMapCompletable(existing -> replace(domain, existing, written, principal));
    }

    private Completable replace(Domain domain, List<TrustDomain> existing, List<TrustedIssuer> written, User principal) {
        Map<String, TrustDomain> byIssuer = new LinkedHashMap<>();
        existing.forEach(trustDomain -> byIssuer.put(trustDomain.getIssuer(), trustDomain));

        List<TrustedIssuer> declared = written.stream()
                .filter(issuer -> issuer != null && issuer.getIssuer() != null && !issuer.getIssuer().isBlank())
                .toList();
        Set<String> declaredIssuers = declared.stream().map(TrustedIssuer::getIssuer).collect(toSet());
        Map<String, String> derivedNames = TrustedIssuerNaming.deriveNames(
                declared.stream().map(TrustedIssuer::getIssuer).filter(issuer -> !byIssuer.containsKey(issuer)).toList(),
                existing.stream().map(TrustDomain::getName).collect(toSet()));

        Completable upserts = Flowable.fromIterable(declared)
                .concatMapCompletable(issuer -> {
                    TrustDomain match = byIssuer.get(issuer.getIssuer());
                    return match != null
                            ? trustDomainService.update(domain, match.getId(), asUpdate(issuer, match), principal).ignoreElement()
                            : trustDomainService.create(domain, asNew(issuer, derivedNames.get(issuer.getIssuer())), principal).ignoreElement();
                });
        Completable withdrawals = Flowable.fromIterable(existing)
                .filter(trustDomain -> !declaredIssuers.contains(trustDomain.getIssuer()))
                .concatMapCompletable(trustDomain -> withdraw(domain, trustDomain, principal));

        return upserts.andThen(withdrawals);
    }

    /**
     * Drops an issuer the written list no longer declares, keeping the row when the trusted domain
     * also issues towards the authority: the deprecated settings must not delete what they cannot see.
     */
    private Completable withdraw(Domain domain, TrustDomain trustDomain, User principal) {
        return trustDomain.trustsCrossAppAccess()
                ? trustDomainService.update(domain, trustDomain.getId(), asIssuerCleared(trustDomain), principal).ignoreElement()
                : trustDomainService.delete(domain, trustDomain.getId(), principal);
    }

    private Single<List<TrustDomain>> tokenExchangeTrustDomains(String domainId) {
        return trustDomainService.findByReference(ReferenceType.DOMAIN, domainId)
                .filter(TrustDomain::trustsTokenExchange)
                .toList();
    }

    private static TrustedIssuer asTrustedIssuer(TrustDomain trustDomain) {
        TrustedIssuer trustedIssuer = new TrustedIssuer();
        trustedIssuer.setIssuer(trustDomain.getIssuer());
        trustedIssuer.setScopeMappings(trustDomain.getScopeMappings());
        trustedIssuer.setUserBindingEnabled(trustDomain.isUserBindingEnabled());
        trustedIssuer.setUserBindingCriteria(trustDomain.getUserBindingCriteria());
        TrustDomainKeyMaterial keyMaterial = trustDomain.getKeyMaterial();
        if (keyMaterial != null && keyMaterial.getSource() == KeyMaterialSource.JWKS_URL) {
            trustedIssuer.setKeyResolutionMethod(KeyResolutionMethod.JWKS_URL);
            trustedIssuer.setJwksUri(keyMaterial.getJwksUrl());
        } else if (keyMaterial != null && keyMaterial.getSource() == KeyMaterialSource.PEM) {
            trustedIssuer.setKeyResolutionMethod(KeyResolutionMethod.PEM);
            trustedIssuer.setCertificate(keyMaterial.getCertificate());
        }
        return trustedIssuer;
    }

    private static NewTrustDomain asNew(TrustedIssuer issuer, String name) {
        NewTrustDomain newTrustDomain = new NewTrustDomain();
        newTrustDomain.setName(name);
        newTrustDomain.setIssuer(issuer.getIssuer());
        newTrustDomain.setKeyMaterial(keyMaterialOf(issuer));
        newTrustDomain.setScopeMappings(issuer.getScopeMappings());
        newTrustDomain.setUserBindingEnabled(issuer.isUserBindingEnabled());
        newTrustDomain.setUserBindingCriteria(issuer.getUserBindingCriteria());
        return newTrustDomain;
    }

    private static UpdateTrustDomain asUpdate(TrustedIssuer issuer, TrustDomain existing) {
        UpdateTrustDomain updateTrustDomain = new UpdateTrustDomain();
        updateTrustDomain.setDescription(existing.getDescription());
        updateTrustDomain.setIssuer(issuer.getIssuer());
        updateTrustDomain.setSpiffeTrustDomain(existing.getSpiffeTrustDomain());
        updateTrustDomain.setKeyMaterial(keyMaterialOf(issuer));
        updateTrustDomain.setScopeMappings(issuer.getScopeMappings());
        updateTrustDomain.setUserBindingEnabled(issuer.isUserBindingEnabled());
        updateTrustDomain.setUserBindingCriteria(issuer.getUserBindingCriteria());
        updateTrustDomain.setCrossAppAccess(existing.getCrossAppAccess());
        return updateTrustDomain;
    }

    /**
     * Clears the issuer and what only makes sense alongside it. A blank issuer removes it; a null one
     * would leave it untouched.
     */
    private static UpdateTrustDomain asIssuerCleared(TrustDomain existing) {
        UpdateTrustDomain updateTrustDomain = new UpdateTrustDomain();
        updateTrustDomain.setName(existing.getName());
        updateTrustDomain.setDescription(existing.getDescription());
        updateTrustDomain.setSpiffeTrustDomain(existing.getSpiffeTrustDomain());
        updateTrustDomain.setIssuer(CLEAR_ISSUER);
        updateTrustDomain.setKeyMaterial(existing.getKeyMaterial());
        updateTrustDomain.setScopeMappings(Map.of());
        updateTrustDomain.setUserBindingEnabled(false);
        updateTrustDomain.setUserBindingCriteria(List.of());
        updateTrustDomain.setCrossAppAccess(existing.getCrossAppAccess());
        return updateTrustDomain;
    }

    private static TrustDomainKeyMaterial keyMaterialOf(TrustedIssuer issuer) {
        KeyResolutionMethod method = issuer.getKeyResolutionMethod();
        if (method == null) {
            return null;
        }
        return switch (method) {
            case JWKS_URL -> TrustDomainKeyMaterial.builder()
                    .source(KeyMaterialSource.JWKS_URL)
                    .jwksUrl(issuer.getJwksUri())
                    .build();
            case PEM -> TrustDomainKeyMaterial.builder()
                    .source(KeyMaterialSource.PEM)
                    .certificate(issuer.getCertificate())
                    .build();
        };
    }
}

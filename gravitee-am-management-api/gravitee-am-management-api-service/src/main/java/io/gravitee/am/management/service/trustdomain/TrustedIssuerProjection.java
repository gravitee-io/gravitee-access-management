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
import io.gravitee.am.model.oidc.TrustDomainKind;
import io.gravitee.am.model.oidc.TrustDomainTokenExchangeSettings;
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
 * with token-exchange trusted domains, which are the only place external trust is stored.
 */
@Component
public class TrustedIssuerProjection {

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
        existing.forEach(trustDomain -> byIssuer.put(trustDomain.issuer(), trustDomain));

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
        Completable deletions = Flowable.fromIterable(existing)
                .filter(trustDomain -> !declaredIssuers.contains(trustDomain.issuer()))
                .concatMapCompletable(trustDomain -> trustDomainService.delete(domain, trustDomain.getId(), principal));

        return upserts.andThen(deletions);
    }

    private Single<List<TrustDomain>> tokenExchangeTrustDomains(String domainId) {
        return trustDomainService.findByReference(ReferenceType.DOMAIN, domainId)
                .filter(trustDomain -> trustDomain.getKind() == TrustDomainKind.TOKEN_EXCHANGE)
                .filter(trustDomain -> trustDomain.issuer() != null)
                .toList();
    }

    private static TrustedIssuer asTrustedIssuer(TrustDomain trustDomain) {
        TrustDomainTokenExchangeSettings tokenExchange = trustDomain.getTokenExchange();
        TrustedIssuer trustedIssuer = new TrustedIssuer();
        trustedIssuer.setIssuer(tokenExchange.getIssuer());
        trustedIssuer.setScopeMappings(tokenExchange.getScopeMappings());
        trustedIssuer.setUserBindingEnabled(tokenExchange.isUserBindingEnabled());
        trustedIssuer.setUserBindingCriteria(tokenExchange.getUserBindingCriteria());
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
        newTrustDomain.setKind(TrustDomainKind.TOKEN_EXCHANGE);
        newTrustDomain.setName(name);
        newTrustDomain.setKeyMaterial(keyMaterialOf(issuer));
        newTrustDomain.setTokenExchange(tokenExchangeOf(issuer));
        return newTrustDomain;
    }

    private static UpdateTrustDomain asUpdate(TrustedIssuer issuer, TrustDomain existing) {
        UpdateTrustDomain updateTrustDomain = new UpdateTrustDomain();
        updateTrustDomain.setDescription(existing.getDescription());
        updateTrustDomain.setKeyMaterial(keyMaterialOf(issuer));
        updateTrustDomain.setTokenExchange(tokenExchangeOf(issuer));
        return updateTrustDomain;
    }

    private static TrustDomainTokenExchangeSettings tokenExchangeOf(TrustedIssuer issuer) {
        return TrustDomainTokenExchangeSettings.builder()
                .issuer(issuer.getIssuer())
                .scopeMappings(issuer.getScopeMappings())
                .userBindingEnabled(issuer.isUserBindingEnabled())
                .userBindingCriteria(issuer.getUserBindingCriteria())
                .build();
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

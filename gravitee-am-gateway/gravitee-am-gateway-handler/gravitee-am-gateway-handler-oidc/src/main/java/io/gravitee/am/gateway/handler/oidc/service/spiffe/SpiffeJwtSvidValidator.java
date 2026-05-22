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
package io.gravitee.am.gateway.handler.oidc.service.spiffe;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.model.application.SpiffeApplicationSettings;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.model.oidc.TrustDomain;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stateless validator for the SPIFFE-specific portion of a JWT-SVID. Signature
 * verification is performed elsewhere via the trust-domain bundle; this class
 * checks claims, audience, lifetime, algorithm allow-list and subject binding.
 *
 * <p>Spec: <a href="https://github.com/spiffe/spiffe/blob/main/standards/JWT-SVID.md">SPIFFE JWT-SVID</a>.
 */
public final class SpiffeJwtSvidValidator {

    public static final String SPIFFE_PREFIX = "spiffe://";

    private static final Set<String> FORBIDDEN_ALGS = Set.of("none", "HS256", "HS384", "HS512");

    private final SpiffeDomainSettings domainSettings;

    public SpiffeJwtSvidValidator(SpiffeDomainSettings domainSettings) {
        this.domainSettings = domainSettings != null ? domainSettings : SpiffeDomainSettings.defaultSettings();
    }

    /**
     * @return null on success, or a short, audit-friendly failure reason.
     */
    public String validate(SignedJWT jwt,
                           TrustDomain trustDomain,
                           SpiffeApplicationSettings spiffeApplicationSettings,
                           String tokenEndpoint) {
        if (jwt == null || trustDomain == null) {
            return "missing input";
        }

        // Algorithm allow-list (SPIFFE JWT-SVID §4): reject 'none' and HMAC variants.
        String alg = jwt.getHeader().getAlgorithm() != null ? jwt.getHeader().getAlgorithm().getName() : null;
        if (alg == null || FORBIDDEN_ALGS.contains(alg)) {
            return "forbidden algorithm: " + alg;
        }
        List<String> allowed = trustDomain.getAllowedAlgorithms() != null && !trustDomain.getAllowedAlgorithms().isEmpty()
                ? trustDomain.getAllowedAlgorithms()
                : domainSettings.getDefaultAllowedAlgorithms();
        if (allowed == null || allowed.stream().noneMatch(a -> a.equalsIgnoreCase(alg))) {
            return "algorithm not allowed: " + alg;
        }

        // Header type: SPIFFE JWT-SVID §3 mandates typ=JWT (not JWT-SVID).
        if (jwt.getHeader().getType() != null && !"JWT".equals(jwt.getHeader().getType().getType())) {
            return "unexpected typ header: " + jwt.getHeader().getType();
        }

        final JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            return "unable to parse claims";
        }

        // Subject must be a SPIFFE URI inside the configured trust domain.
        String sub = claims.getSubject();
        if (sub == null || !sub.startsWith(SPIFFE_PREFIX)) {
            return "sub is not a SPIFFE ID";
        }
        String subTrustDomain = trustDomainOf(sub);
        if (subTrustDomain == null) {
            return "sub has no trust domain";
        }
        if (!subTrustDomain.equalsIgnoreCase(trustDomain.getName())) {
            return "trust-domain mismatch: " + subTrustDomain;
        }

        // Subject ↔ application binding.
        if (spiffeApplicationSettings == null) {
            return "client missing spiffe settings";
        }
        String expected = spiffeApplicationSettings.getSubject();
        if (expected == null || expected.isBlank()) {
            return "sub does not match client subject";
        }
        SpiffeApplicationSettings.SubjectMatchMode mode = spiffeApplicationSettings.getSubjectMatchMode();
        if (mode == null) {
            mode = SpiffeApplicationSettings.SubjectMatchMode.EXACT;
        }
        boolean match = switch (mode) {
            case EXACT -> expected.equals(sub);
            case PREFIX -> sub.startsWith(expected);
        };
        if (!match) {
            return "sub does not match client subject";
        }

        // Audience must include AM's token endpoint.
        List<String> audiences = claims.getAudience();
        if (audiences == null || !audiences.contains(tokenEndpoint)) {
            return "aud does not contain token endpoint";
        }

        // Lifetime / clock-skew (SPIFFE JWT-SVID §3 — short-lived, ≤ 5 min recommended).
        Instant now = Instant.now();
        Duration skew = Duration.ofSeconds(domainSettings.getClockSkewSeconds());
        if (claims.getIssueTime() == null) {
            return "iat is required";
        }
        Instant iat = claims.getIssueTime().toInstant();
        if (iat.isAfter(now.plus(skew))) {
            return "iat in the future";
        }
        if (claims.getExpirationTime() == null) {
            return "exp is required";
        }
        Instant exp = claims.getExpirationTime().toInstant();
        if (exp.isBefore(now.minus(skew))) {
            return "expired";
        }
        long lifetime = Duration.between(iat, exp).toSeconds();
        if (lifetime > domainSettings.getMaxJwtLifetimeSeconds()) {
            return "lifetime exceeds max " + domainSettings.getMaxJwtLifetimeSeconds() + "s";
        }
        if (claims.getNotBeforeTime() != null
                && claims.getNotBeforeTime().toInstant().isAfter(now.plus(skew))) {
            return "nbf in the future";
        }

        return null;
    }

    public static String trustDomainOf(String spiffeId) {
        if (spiffeId == null || !spiffeId.startsWith(SPIFFE_PREFIX)) {
            return null;
        }
        String rest = spiffeId.substring(SPIFFE_PREFIX.length());
        int slash = rest.indexOf('/');
        String td = slash < 0 ? rest : rest.substring(0, slash);
        return td.isEmpty() ? null : td.toLowerCase(Locale.ROOT);
    }

    public static boolean isSpiffeId(String value) {
        return value != null && value.startsWith(SPIFFE_PREFIX);
    }
}

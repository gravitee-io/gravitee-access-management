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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

public final class TrustedIssuerNaming {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int DIGEST_LENGTH = 8;
    private static final Pattern OUTSIDE_LABEL = Pattern.compile("[^a-z0-9.-]+");
    private static final Pattern LABEL_EDGES = Pattern.compile("(?:^[.-]+)|(?:[.-]+$)");

    private TrustedIssuerNaming() {
        throw new UnsupportedOperationException("utility class, don't instantiate");
    }

    /**
     * Derives the trusted-domain name each issuer URL maps to, keyed by issuer URL. Derivation is
     * deterministic and order-independent, so identical configuration yields identical names in
     * every environment; an issuer whose slug would collide with another issuer's or with an
     * already taken name carries a digest of its URL.
     */
    public static Map<String, String> deriveNames(Collection<String> issuers, Set<String> takenNames) {
        Map<String, Long> occurrences = issuers.stream()
                .collect(groupingBy(TrustedIssuerNaming::slugOf, counting()));
        Map<String, String> names = new LinkedHashMap<>();
        issuers.forEach(issuer -> {
            String slug = slugOf(issuer);
            boolean ambiguous = slug.isEmpty() || occurrences.get(slug) > 1 || takenNames.contains(slug);
            names.put(issuer, ambiguous ? disambiguate(issuer, slug) : slug);
        });
        return names;
    }

    private static String slugOf(String issuer) {
        return slugOf(issuer, MAX_NAME_LENGTH);
    }

    private static String slugOf(String issuer, int maxLength) {
        String slug = trimEdges(OUTSIDE_LABEL.matcher(issuer.toLowerCase(Locale.ROOT)).replaceAll("-"));
        return slug.length() > maxLength ? trimEdges(slug.substring(0, maxLength)) : slug;
    }

    private static String trimEdges(String slug) {
        return LABEL_EDGES.matcher(slug).replaceAll("");
    }

    private static String disambiguate(String issuer, String slug) {
        String digest = digestOf(issuer);
        if (slug.isEmpty()) {
            return digest;
        }
        return slugOf(issuer, MAX_NAME_LENGTH - digest.length() - 1) + "-" + digest;
    }

    private static String digestOf(String issuer) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(issuer.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, DIGEST_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to name a migrated trusted domain", e);
        }
    }
}

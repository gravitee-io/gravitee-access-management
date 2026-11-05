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
package io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.gravitee.am.model.oauth2.AuthorizationDetail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SHA-256 digests that identify authorization details by their JSON value. Member order is ignored, nested array
 * order is not.
 */
public final class AuthorizationDetailDigest {

    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private AuthorizationDetailDigest() {
    }

    public static String of(Map<String, ?> detail) {
        try {
            return sha256(CANONICAL.writeValueAsString(detail));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize authorization detail", e);
        }
    }

    /**
     * The order of the details is ignored.
     */
    public static String ofAll(Collection<AuthorizationDetail> details) {
        return sha256(details.stream()
                .map(AuthorizationDetailDigest::of)
                .sorted()
                .collect(Collectors.joining(",")));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

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

import lombok.Builder;
import lombok.With;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

@Builder(toBuilder = true)
public record AccountAccessToken(String tokenId,
                                 ReferenceType referenceType,
                                 String referenceId,
                                 String userId,
                                 String issuerUsername,
                                 String issuerId,
                                 String name,
                                 @With String token,
                                 Date createdAt,
                                 Date updatedAt) {

    // response for "create" operation is the only time this class
    // will store the actual raw token
    public AccountAccessToken toCreateResponse(String rawToken) {
        var fullToken = "%s.%s".formatted(tokenId, rawToken);
        var opaqueToken = Base64.getEncoder().encodeToString(fullToken.getBytes(StandardCharsets.UTF_8));
        return toBuilder()
                .token(opaqueToken)
                .build();
    }

    /**
     * Decode the bearer wire format {@code Base64(tokenId + "." + tokenValue)} produced by
     * {@link #toCreateResponse(String)}. Throws {@link IllegalArgumentException} if the input is
     * not valid Base64 or the decoded payload is not exactly two dot-separated parts.
     */
    public static Decoded decode(String encodedToken) {
        var decodedBytes = Base64.getDecoder().decode(encodedToken.getBytes(StandardCharsets.UTF_8));
        var decodedText = new String(decodedBytes, StandardCharsets.UTF_8);
        var parts = decodedText.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed account access token");
        }
        return new Decoded(parts[0], parts[1]);
    }

    /**
     * True iff the bearer value has JWT shape (contains a literal {@code '.'}).
     */
    public static boolean hasJwtShape(String bearerValue) {
        return bearerValue.contains(".");
    }

    /**
     * The two components of an account access token as carried on the wire (after Base64 decoding
     * the bearer value produced by {@link #toCreateResponse(String)}).
     */
    public record Decoded(String tokenId, String tokenValue) {
    }
}

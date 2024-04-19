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

}

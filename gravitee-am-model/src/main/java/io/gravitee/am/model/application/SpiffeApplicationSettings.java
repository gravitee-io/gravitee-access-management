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
package io.gravitee.am.model.application;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-application SPIFFE settings, populated when the application uses the
 * {@code spiffe_jwt} client-authentication method.
 */
@Getter
@Setter
@NoArgsConstructor
public class SpiffeApplicationSettings {

    /** Name of the {@code TrustDomain} this application authenticates against. */
    private String trustDomain;

    /** Exact SPIFFE ID expected in the SVID's {@code sub} claim. */
    private String subject;

    public SpiffeApplicationSettings(SpiffeApplicationSettings other) {
        this.trustDomain = other.trustDomain;
        this.subject = other.subject;
    }
}

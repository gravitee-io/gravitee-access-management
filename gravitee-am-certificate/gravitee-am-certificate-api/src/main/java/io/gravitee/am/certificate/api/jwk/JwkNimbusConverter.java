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
package io.gravitee.am.certificate.api.jwk;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.gravitee.am.model.jose.JWK;
import lombok.NonNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public interface JwkNimbusConverter {

    Stream<JWK> createJwk();

    static JwkNimbusConverter converter(@NonNull com.nimbusds.jose.jwk.JWK nimbusJwk,
                                        @NonNull Boolean includePrivate,
                                        Set<String> usage,
                                        String algorithm) {
        if (nimbusJwk instanceof RSAKey rsa) {
            return new JwkNimbusRSAConverter(rsa, includePrivate, usage(usage, nimbusJwk), algorithm);
        } else if (nimbusJwk instanceof ECKey ec) {
            return new JwkNimbusECConverter(ec, includePrivate, usage(usage, nimbusJwk), algorithm);
        } else {
            throw new IllegalArgumentException("Unknown implementation=" + nimbusJwk);
        }
    }

    private static Set<String> usage(Set<String> usage, com.nimbusds.jose.jwk.JWK nimbusJwk) {
        // if the user doesn't provide usage, let the certificate define it or use "sig" as default
        if (usage != null && !usage.isEmpty()) {
            return usage;
        }
        if (nimbusJwk.getKeyUse() != null) {
            return Set.of(nimbusJwk.getKeyUse().identifier());
        } else {
            return Set.of(KeyUse.SIGNATURE.getValue());
        }
    }
}

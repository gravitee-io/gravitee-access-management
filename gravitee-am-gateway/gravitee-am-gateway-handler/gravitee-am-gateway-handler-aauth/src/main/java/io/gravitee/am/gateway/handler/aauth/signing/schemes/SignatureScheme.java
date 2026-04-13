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
package io.gravitee.am.gateway.handler.aauth.signing.schemes;

import io.gravitee.am.gateway.handler.aauth.signing.SignatureKeyInfo;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureVerificationException;

/**
 * Interface for resolving a public key from a Signature-Key header by scheme.
 * Phase 2 implements HWK; Phase 3 adds JWKS_URI; Phase 8 adds JWT.
 */
public interface SignatureScheme {

    /**
     * Resolve the public key and metadata from the Signature-Key header params.
     *
     * @param keyInfo the parsed Signature-Key header
     * @return resolved public key, algorithm, and JWK thumbprint
     * @throws SignatureVerificationException if the key cannot be resolved
     */
    ResolvedKey resolve(SignatureKeyInfo keyInfo) throws SignatureVerificationException;
}

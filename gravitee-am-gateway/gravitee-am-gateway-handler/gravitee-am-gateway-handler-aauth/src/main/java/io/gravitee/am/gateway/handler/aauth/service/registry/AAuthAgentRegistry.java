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
package io.gravitee.am.gateway.handler.aauth.service.registry;

import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.model.Application;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Registry that maps verified AAUTH agent identities to {@link Application} entities.
 * <p>
 * On first verified contact from an agent with a metadata URL (jwks_uri or jwt scheme),
 * the registry auto-creates an {@code Application(type=AAUTH_AGENT)}. Subsequent contacts
 * reuse the same Application. Pseudonymous agents (hwk scheme without a metadata URL)
 * resolve to {@link Maybe#empty()}.
 */
public interface AAuthAgentRegistry {

    /**
     * Resolve or auto-create the Application for the given verified agent.
     *
     * @param verification the signature verification result (must include agentServerUrl for non-pseudonymous)
     * @param domainId     the security domain ID
     * @return the Application, or empty for pseudonymous mode
     */
    Maybe<Application> resolveOrCreate(VerificationResult verification, String domainId);
}

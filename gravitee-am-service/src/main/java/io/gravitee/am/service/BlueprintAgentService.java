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
package io.gravitee.am.service;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.jose.JWK;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * Service for managing Blueprint agent identity features on applications:
 * JWKS management, agent settings validation.
 */
public interface BlueprintAgentService {

    /**
     * Add a public key to the application's agent JWKS.
     * Enforces the maxPublicKeysPerWorkload limit from agent settings.
     *
     * @param applicationId the application ID
     * @param key the JWK to add (must have a kid)
     * @param principal the authenticated user performing the action (for audit attribution)
     * @return the updated application
     */
    Single<Application> addAgentKey(String applicationId, JWK key, User principal);

    /**
     * Remove a public key from the application's agent JWKS by kid.
     *
     * @param applicationId the application ID
     * @param kid the key ID to remove
     * @param principal the authenticated user performing the action (for audit attribution)
     * @return the updated application
     */
    Single<Application> removeAgentKey(String applicationId, String kid, User principal);

    /**
     * List all public keys in the application's agent JWKS.
     *
     * @param applicationId the application ID
     * @return list of JWKs
     */
    Single<List<JWK>> listAgentKeys(String applicationId);
}

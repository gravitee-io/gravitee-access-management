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
package io.gravitee.am.authorizationengine.api.audit;

/**
 * Callback interface for reporting authorization evaluation events from an engine provider
 * back to the gateway's audit pipeline.
 * <p>
 * The gateway's {@code AuthorizationEngineManager} provides an implementation of this callback
 * to each provider during deployment. The provider calls {@link #onEvaluation} whenever
 * an authorization decision is made (either locally or received from an external sidecar).
 *
 * @author GraviteeSource Team
 */
@FunctionalInterface
public interface AuthorizationAuditCallback {

    /**
     * Called when an authorization evaluation has been performed.
     *
     * @param event the evaluation event containing decision details
     */
    void onEvaluation(AuthorizationAuditEvent event);
}

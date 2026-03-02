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
package io.gravitee.am.authorizationengine.api.ws;

import io.gravitee.am.authorizationengine.api.audit.AuthorizationAuditEvent;

/**
 * Sealed message hierarchy for the gateway ↔ sidecar WebSocket protocol.
 * <p>
 * Sidecar → Gateway: {@link BundleCheck}, {@link AuditEvent}
 * Gateway → Sidecar: {@link BundleCurrent}, {@link BundleUpdate}, {@link Error}
 *
 * @author GraviteeSource Team
 */
public sealed interface WsMessage {

    /** Sidecar → Gateway: request bundle if version differs. */
    record BundleCheck(int version) implements WsMessage {}

    /** Gateway → Sidecar: sidecar is already up to date. */
    record BundleCurrent() implements WsMessage {}

    /** Gateway → Sidecar: new bundle content. */
    record BundleUpdate(int version, String policy, String data, String schema) implements WsMessage {}

    /** Sidecar → Gateway: audit event from a policy evaluation. */
    record AuditEvent(AuthorizationAuditEvent event) implements WsMessage {}

    /** Gateway → Sidecar: error message. */
    record Error(String code, String message) implements WsMessage {}
}

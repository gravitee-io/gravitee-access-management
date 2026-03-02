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

/**
 * Immutable snapshot of a resolved authorization bundle with a monotonically increasing version number.
 * Cached by the gateway and served to sidecars over WebSocket when their local version is stale.
 *
 * @param version monotonically increasing version counter (starts at 1)
 * @param policy  resolved policy text (engine-specific)
 * @param data    resolved entity/tuple data (JSON string)
 * @param schema  resolved schema definition (JSON string, may be null)
 * @author GraviteeSource Team
 */
public record ResolvedBundleSnapshot(
        int version,
        String policy,
        String data,
        String schema
) {}

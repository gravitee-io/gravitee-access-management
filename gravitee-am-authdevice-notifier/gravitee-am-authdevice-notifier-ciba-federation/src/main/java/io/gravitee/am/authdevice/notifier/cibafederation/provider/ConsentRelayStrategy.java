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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import java.util.List;
import java.util.Map;

/**
 * Optional transform applied to a request's RFC 9396 {@code authorization_details} before they are
 * relayed to the downstream IdP on bc-authorize. When no strategy is selected the details are relayed
 * unchanged (raw RAR). Implementations are discovered on the plugin classpath via SpringFactoriesLoader
 * and selected per-notifier by {@link #id()}; they must be stateless and no-arg constructible.
 */
public interface ConsentRelayStrategy {

    /** Stable id used to select this strategy from notifier configuration. */
    String id();

    /** Transform (or pass through) the inbound authorization_details for relay; per-notifier input via {@code ctx}. */
    List<Map<String, Object>> relay(List<Map<String, Object>> authorizationDetails, ConsentRelayContext ctx);
}

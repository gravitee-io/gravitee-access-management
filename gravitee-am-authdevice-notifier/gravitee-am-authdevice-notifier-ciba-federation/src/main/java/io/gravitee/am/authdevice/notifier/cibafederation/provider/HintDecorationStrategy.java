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

/**
 * Optional transform applied to a request's CIBA hint(s) before they are relayed to the downstream
 * IdP on bc-authorize. When no strategy is selected the hint is relayed verbatim. Implementations are
 * discovered on the plugin classpath via SpringFactoriesLoader and selected per-notifier by {@link #id()};
 * they must be stateless and no-arg constructible. The core plugin ships none and knows nothing of any
 * IdP-specific hint format.
 */
public interface HintDecorationStrategy {

    /** Stable id used to select this strategy from notifier configuration. */
    String id();

    /** Decorate (or pass through) the inbound hint(s) for relay; per-notifier input via {@code ctx}. */
    CibaHints decorate(CibaHints inbound, HintDecorationContext ctx);
}

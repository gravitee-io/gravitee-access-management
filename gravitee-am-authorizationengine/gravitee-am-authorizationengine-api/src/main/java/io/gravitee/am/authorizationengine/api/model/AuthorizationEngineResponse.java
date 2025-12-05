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
package io.gravitee.am.authorizationengine.api.model;

import lombok.Builder;

import java.util.Map;

/**
 * Authorization engine response.
 *
 * @param decisionId Unique identifier for this authorization decision
 * @param decision   Whether access is granted (true) or denied (false)
 * @param context    Optional context with additional decision information (e.g., reason)
 * @author GraviteeSource Team
 */
@Builder
public record AuthorizationEngineResponse(
        String decisionId,
        boolean decision,
        Map<String, Object> context
) {}

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
package io.gravitee.am.gateway.handler.oauth2.resources.request;

import io.gravitee.am.common.oauth2.Parameters;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility class for parsing RFC 8707 resource parameters from HTTP requests.
 * Handles both single and multiple resource parameters, including empty values for validation.
 *
 * @author GraviteeSource Team
 */
public final class ResourceParameterUtils {

    private ResourceParameterUtils() {
        // Utility class
    }

    /**
     * Parses resource parameters from a RoutingContext (used by AuthorizationRequestFactory).
     * 
     * @param context the routing context containing the request
     * @return Set of resource identifiers (may be empty if none present)
     */
    public static Set<String> parseResourceParameters(RoutingContext context) {
        return parseResourceParameters(context.request());
    }

    /**
     * Parses resource parameters from an HttpServerRequest (used by TokenRequestFactory).
     * 
     * @param request the HTTP server request
     * @return Set of resource identifiers (may be empty if none present)
     */
    public static Set<String> parseResourceParameters(HttpServerRequest request) {
        List<String> resourceParams = request.params().getAll(Parameters.RESOURCE);
        
        if (resourceParams == null || resourceParams.isEmpty()) {
            return java.util.Collections.emptySet();
        }

        return resourceParams
            .stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

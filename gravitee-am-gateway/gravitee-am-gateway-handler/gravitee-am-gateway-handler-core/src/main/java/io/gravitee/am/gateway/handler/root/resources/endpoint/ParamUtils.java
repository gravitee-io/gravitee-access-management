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
package io.gravitee.am.gateway.handler.root.resources.endpoint;

import com.nimbusds.jwt.JWT;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;

import static java.util.Objects.nonNull;

/**
 * This utility class is used to extract OAuth parameters either from the query parameters
 * or from the RequestObject.
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ParamUtils {
    private static Logger LOGGER = LoggerFactory.getLogger(ParamUtils.class);

    public static Set<String> splitScopes(String scope) {
        HashSet<String> scopes = scope != null && !scope.isEmpty() ? new HashSet<>(Arrays.asList(scope.split("\\s+"))) : null;
        return scopes;
    }

    public static List<String> splitAcrValues(String values) {
        List<String> acrValues = values != null && !values.isEmpty() ? Arrays.asList(values.split("\\s+")) : null;
        return acrValues;
    }

    public static String getOAuthParameter(RoutingContext context, String paramName) {
        Optional<String> value = Optional.empty();
        final JWT requestObject = context.get(ConstantKeys.REQUEST_OBJECT_KEY);
        if (requestObject != null) {
            try {
                // return parameter from the request object first as When the request parameter (or request_uri) is used,
                // the OpenID Connect request parameter values contained in the JWT supersede those passed using the OAuth 2.0 request syntax.
                final Object claim = requestObject.getJWTClaimsSet().getClaim(paramName);
                if (Parameters.CLAIMS.equals(paramName) && claim != null) {
                    value = Optional.ofNullable(Json.encode(claim));
                } else {
                    if (claim != null) {
                        // request_expiry may be an integer so get Generic object type and convert it in string
                        value = Optional.ofNullable(claim.toString());
                    } else {
                        value = Optional.empty();
                    }
                }
            } catch (ParseException e) {
                LOGGER.warn("Unable to extract parameter '{}' from RequestObject", paramName);
            }
        }
        // if parameter is missing from the request object (or if the extract fails)
        // return the value provided through query parameters
        return value.orElse(context.request().getParam(paramName));
    }

    public static boolean redirectMatches(String requestedRedirect, String registeredClientUri, boolean uriStrictMatch) {
        if (uriStrictMatch) {
            return Objects.equals(requestedRedirect, registeredClientUri);
        }

        // nominal case
        try {
            var requestedUrl = new URL(requestedRedirect);
            var registeredUrl = new URL(registeredClientUri);

            final String requestedProtocol = requestedUrl.getProtocol();
            final String requestHost = requestedUrl.getHost();

            int requestedPort = requestedUrl.getPort() != -1 ? requestedUrl.getPort() : requestedUrl.getDefaultPort();
            int registeredPort = registeredUrl.getPort() != -1 ? registeredUrl.getPort() : registeredUrl.getDefaultPort();

            final String hostPattern = buildPattern(registeredUrl.getHost());
            boolean portsMatch = registeredPort == requestedPort;

            if (registeredUrl.getProtocol().equals(requestedProtocol) && portsMatch) {
                // We keep the logic from previous behaviour not to break the configuration
                if (!registeredClientUri.contains("*") && Objects.equals(requestHost, registeredUrl.getHost())) {
                    return requestedUrl.getPath().startsWith(registeredUrl.getPath());
                }
                // else we use the enhanced wildcard feature
                else if (nonNull(hostPattern) && requestHost.matches(hostPattern)) {
                    final String requestedUrlPath = requestedUrl.getPath();
                    final String registeredUrlPath = registeredUrl.getPath();
                    if(requestedUrlPath.isEmpty() && (registeredUrlPath.isEmpty() || registeredUrlPath.startsWith("*") || registeredUrlPath.startsWith("/*"))) {
                        return true;
                    }
                    String pathPattern = buildPattern(registeredUrlPath);
                    return requestedUrlPath.matches(pathPattern);
                }
            }
        } catch (MalformedURLException e) {
            LOGGER.debug("An unexpected error has occurred", e);
        }
        return Objects.equals(requestedRedirect, registeredClientUri);
    }

    private static String buildPattern(String patternPath) {
        if (patternPath == null) {
            return null;
        }
        var patternBuilder = new StringBuilder();
        final char[] patternArray = patternPath.toCharArray();
        int firstIndex = 0;
        for (int i = 0; i < patternArray.length; i++) {
            char c = patternArray[i];
            if (c == '*') {
                if (firstIndex != i) {
                    patternBuilder.append(Pattern.quote(patternPath.substring(firstIndex, i)));
                }
                patternBuilder.append(".*");
                firstIndex = i + 1;
            }
        }
        //There is no wildcard so we need to add the whole string
        if (firstIndex == 0){
            patternBuilder.append(patternPath);
        }
        return patternBuilder.toString();
    }
}

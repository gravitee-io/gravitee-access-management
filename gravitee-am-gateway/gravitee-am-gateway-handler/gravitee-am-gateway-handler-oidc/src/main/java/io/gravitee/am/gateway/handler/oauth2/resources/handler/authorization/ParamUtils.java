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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import com.nimbusds.jwt.JWT;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Optional;

/**
 * This utility class is used to extract OAuth parameters either from the query parameters
 * or from the RequestObject.
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ParamUtils {
    private static Logger LOGGER = LoggerFactory.getLogger(ParamUtils.class);

    public static String getOAuthParameter(RoutingContext context, String paramName) {
        Optional<String> value = Optional.empty();
        final JWT requestObject = context.get(ConstantKeys.REQUEST_OBJECT_KEY);
        if (requestObject != null) {
            try {
                // return parameter from the request object first as When the request parameter (or request_uri) is used,
                // the OpenID Connect request parameter values contained in the JWT supersede those passed using the OAuth 2.0 request syntax.
                if (Parameters.CLAIMS.equals(paramName) && requestObject.getJWTClaimsSet().getClaim(paramName) != null) {
                    value = Optional.ofNullable(Json.encode(requestObject.getJWTClaimsSet().getClaim(paramName)));
                } else {
                    value = Optional.ofNullable(requestObject.getJWTClaimsSet().getStringClaim(paramName));
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
            return requestedRedirect.equals(registeredClientUri);
        }

        // nominal case
        try {
            URL req = new URL(requestedRedirect);
            URL reg = new URL(registeredClientUri);

            int requestedPort = req.getPort() != -1 ? req.getPort() : req.getDefaultPort();
            int registeredPort = reg.getPort() != -1 ? reg.getPort() : reg.getDefaultPort();

            boolean portsMatch = registeredPort == requestedPort;

            if (reg.getProtocol().equals(req.getProtocol()) &&
                    reg.getHost().equals(req.getHost()) &&
                    portsMatch) {
                return req.getPath().startsWith(reg.getPath());
            }
        } catch (MalformedURLException e) {

        }

        return requestedRedirect.equals(registeredClientUri);
    }
}

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

package io.gravitee.am.gateway.handler.common.utils;


import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.web.UriBuilder;
import io.vertx.rxjava3.core.MultiMap;
import org.springframework.util.StringUtils;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UsernameHelper {
    /**
     *  This conditional block has been added specifically for https://github.com/gravitee-io/issues/issues/7889 & https://github.com/gravitee-io/issues/issues/10469
     *  we need to encode email address that contains a '+' to avoid
     *  white space in username when landing to the login form.
     *  And we have to do this because the UriBuilderRequest.resolveProxyRequest decode & encode parameter to avoid
     *  double encoding during the redirect...
     *  we restrict to the login_hint and username to avoid side effect
     *  
     * @param queryParams 
     * @param paramName
     */
    public static void escapeUsernameParam(MultiMap queryParams, String paramName) {
        if (queryParams.contains(paramName)) {
            final String paramValue = queryParams.get(paramName);
            if (!UriBuilder.decodeURIComponent(paramValue).equals(paramValue)
                    && paramValue.contains("@")
                    && paramValue.contains("+")) {
                queryParams.set(paramName, StaticEnvironmentProvider.sanitizeParametersEncoding() ? UriBuilder.encodeURIComponent(paramValue) : paramValue);
            }
        }
    }

    public static boolean isEmailWithAlias(String paramName, String value) {
        if ((Parameters.LOGIN_HINT.equals(paramName) || io.gravitee.am.common.oauth2.Parameters.USERNAME.equals(paramName)) && StringUtils.hasText(value)) {
            return value.contains("@") && value.contains("+");
        }
        return false;
    }
}

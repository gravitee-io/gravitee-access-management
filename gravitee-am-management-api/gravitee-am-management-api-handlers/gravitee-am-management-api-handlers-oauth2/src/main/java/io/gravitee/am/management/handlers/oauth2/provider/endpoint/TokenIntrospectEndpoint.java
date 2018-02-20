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
package io.gravitee.am.management.handlers.oauth2.provider.endpoint;

import io.gravitee.am.management.handlers.oauth2.provider.token.DefaultIntrospectionAccessTokenConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.AccessTokenConverter;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
//@Controller
public class TokenIntrospectEndpoint {
    private ResourceServerTokenServices resourceServerTokenServices;
    private AccessTokenConverter accessTokenConverter = new DefaultIntrospectionAccessTokenConverter();

    protected final Logger logger = LoggerFactory.getLogger(TokenIntrospectEndpoint.class);

    public TokenIntrospectEndpoint(ResourceServerTokenServices resourceServerTokenServices) {
        this.resourceServerTokenServices = resourceServerTokenServices;
    }

    /**
     * @param accessTokenConverter the accessTokenConverter to set
     */
    public void setAccessTokenConverter(AccessTokenConverter accessTokenConverter) {
        this.accessTokenConverter = accessTokenConverter;
    }

    @RequestMapping(value = "/introspect")
    @ResponseBody
    public Map<String, ?> introspectToken(@RequestParam("token") String value,
                                          @RequestParam(value = "resource_id", required = false) String resourceId,
                                          @RequestParam(value = "token_type_hint", required = false) String tokenType) {

        OAuth2AccessToken token = resourceServerTokenServices.readAccessToken(value);
        if (token == null || token.isExpired()) {
            Map<String, Object> response = new HashMap<>();
            response.put("active",false);
            return response;
        }

        OAuth2Authentication authentication = resourceServerTokenServices.loadAuthentication(token.getValue());

        return accessTokenConverter.convertAccessToken(token, authentication);
    }
}
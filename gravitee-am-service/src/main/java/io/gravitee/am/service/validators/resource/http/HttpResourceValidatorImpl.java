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

package io.gravitee.am.service.validators.resource.http;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.validators.resource.ResourceValidator;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class HttpResourceValidatorImpl implements HttpResourceValidator {

    private static final String UNEXPECTED_MESSAGE = "An unexpected error has occurred while trying to validate resource [%s]";
    private static final String FIELD_AUTH = "auth";
    private static final String FIELD_TYPE = "type";
    private static final String OAUTH2 = "oauth2";
    private static final String FIELD_ENDPOINT = "endpoint";
    private static final String FIELD_CLIENTID = "clientId";
    private static final String FIELD_CLIENT_SECRET = "clientSecret";
    private static final String HTTP_AM_RESOURCE = "http-am-resource";
    private static final String INVALID_OAUTH_CONF = "OAuth config is missing or invalid";
    private final ObjectMapper objectMapper;

    public HttpResourceValidatorImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<InvalidParameterException> validate(ResourceValidator.ResourceHolder resource) {
        if (HTTP_AM_RESOURCE.equalsIgnoreCase(resource.getName())) {
            try {
                final var configuration = resource.getConfiguration();
                return !useOauth(configuration) || asValidOAuthConfig(configuration) ? Optional.empty() : Optional.of(new InvalidParameterException(INVALID_OAUTH_CONF));
            } catch (Exception e) {
                return Optional.of(new InvalidParameterException(String.format(UNEXPECTED_MESSAGE, resource.getName())));
            }
        }
        return Optional.empty();
    }

    protected boolean useOauth(String configuration) throws JsonProcessingException {
        var tree = objectMapper.readTree(configuration);
        return ofNullable(tree.get(FIELD_AUTH)).map(auth -> auth.get(FIELD_TYPE)).map(JsonNode::asText).map(type -> OAUTH2.equalsIgnoreCase(type)).orElse(false);
    }

    protected boolean asValidOAuthConfig(String configuration) throws JsonProcessingException {
        var tree = objectMapper.readTree(configuration);
        return ofNullable(tree.get(FIELD_AUTH))
                .map(auth -> auth.get(OAUTH2))
                .map(oauth -> oauth.hasNonNull(FIELD_ENDPOINT) &&
                        oauth.hasNonNull(FIELD_CLIENTID) &&
                        oauth.hasNonNull(FIELD_CLIENT_SECRET))
                .orElse(false);
    }
}

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
package io.gravitee.am.identityprovider.http.authentication;

import io.gravitee.am.common.exception.authentication.AuthenticationException;
import io.gravitee.am.common.exception.authentication.InternalAuthenticationServiceException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.http.HttpIdentityProviderMapper;
import io.gravitee.am.identityprovider.http.HttpIdentityProviderResponse;
import io.gravitee.am.identityprovider.http.HttpIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.http.authentication.spring.HttpAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.http.configuration.HttpIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.http.configuration.HttpResourceConfiguration;
import io.gravitee.am.identityprovider.http.configuration.HttpResponseErrorCondition;
import io.gravitee.common.http.HttpHeader;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.gravitee.el.TemplateEngine;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(HttpAuthenticationProviderConfiguration.class)
public class HttpAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpAuthenticationProvider.class);
    private static final String PRINCIPAL_CONTEXT_KEY = "principal";
    private static final String CREDENTIALS_CONTEXT_KEY = "credentials";
    private static final String AUTHENTICATION_RESPONSE_CONTEXT_KEY = "authenticationResponse";

    @Autowired
    private WebClient client;

    @Autowired
    private HttpIdentityProviderConfiguration configuration;

    @Autowired
    private HttpIdentityProviderMapper mapper;

    @Autowired
    private HttpIdentityProviderRoleMapper roleMapper;

    @Override
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        try {
            // prepare context
            TemplateEngine templateEngine = authentication.getContext().getTemplateEngine();
            templateEngine.getTemplateContext().setVariable(PRINCIPAL_CONTEXT_KEY, authentication.getPrincipal());
            templateEngine.getTemplateContext().setVariable(CREDENTIALS_CONTEXT_KEY, authentication.getCredentials());

            // prepare request
            final HttpResourceConfiguration resourceConfiguration = configuration.getAuthenticationResource();
            final String authenticationURI = templateEngine.getValue(resourceConfiguration.getBaseURL(), String.class);
            final HttpMethod authenticationHttpMethod = HttpMethod.valueOf(resourceConfiguration.getHttpMethod().toString());
            final List<HttpHeader> authenticationHttpHeaders = resourceConfiguration.getHttpHeaders();
            final String authenticationBody = resourceConfiguration.getHttpBody();
            final HttpRequest<Buffer> httpRequest = client.requestAbs(authenticationHttpMethod, authenticationURI);
            final List<HttpResponseErrorCondition> errorConditions = resourceConfiguration.getHttpResponseErrorConditions();

            // set headers
            if (authenticationHttpHeaders != null) {
                authenticationHttpHeaders.forEach(header -> {
                    String extValue = templateEngine.getValue(header.getValue(), String.class);
                    httpRequest.putHeader(header.getName(), extValue);
                });
            }

            // set body
            Single<HttpResponse<Buffer>> responseHandler;
            if (authenticationBody != null && !authenticationBody.isEmpty()) {
                String bodyRequest = templateEngine.getValue(authenticationBody, String.class);
                if (!httpRequest.headers().contains(HttpHeaders.CONTENT_TYPE)) {
                    httpRequest.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(bodyRequest.length()));
                    responseHandler = httpRequest.rxSendBuffer(Buffer.buffer(bodyRequest));
                } else {
                    String contentTypeHeader = httpRequest.headers().get(HttpHeaders.CONTENT_TYPE);
                    switch (contentTypeHeader) {
                        case(MediaType.APPLICATION_JSON):
                            responseHandler = httpRequest.rxSendJsonObject(new JsonObject(bodyRequest));
                            break;
                        case(MediaType.APPLICATION_FORM_URLENCODED):
                            Map<String, String> queryParameters = format(bodyRequest);
                            MultiMap multiMap = MultiMap.caseInsensitiveMultiMap();
                            multiMap.setAll(queryParameters);
                            responseHandler = httpRequest.rxSendForm(multiMap);
                            break;
                        default:
                            httpRequest.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(bodyRequest.length()));
                            responseHandler = httpRequest.rxSendBuffer(Buffer.buffer(bodyRequest));
                    }
                }
            } else {
                responseHandler = httpRequest.rxSend();
            }

            return responseHandler
                    .toMaybe()
                    .map(httpResponse -> {
                        String responseBody =  httpResponse.bodyAsString();
                        // put response into template variable for EL
                        templateEngine.getTemplateContext().setVariable(AUTHENTICATION_RESPONSE_CONTEXT_KEY,
                                new HttpIdentityProviderResponse(httpResponse, responseBody));

                        // process authentication response
                        Exception lastException = null;
                        if (errorConditions != null) {
                            Iterator<HttpResponseErrorCondition> iter = errorConditions.iterator();
                            while (iter.hasNext() && lastException == null) {
                                HttpResponseErrorCondition errorCondition = iter.next();
                                if (templateEngine.getValue(errorCondition.getValue(), Boolean.class)) {
                                    Class<? extends Exception> clazz = (Class<? extends Exception>) Class.forName(errorCondition.getException());
                                    if (errorCondition.getMessage() != null) {
                                        String errorMessage = templateEngine.getValue(errorCondition.getMessage(), String.class);
                                        Constructor<?> constructor = clazz.getConstructor(String.class);
                                        lastException = clazz.cast(constructor.newInstance(new Object[]{errorMessage}));
                                    } else {
                                        lastException = clazz.newInstance();
                                    }
                                }
                            }
                        }

                        // if user authentication failed, throw exception
                        if (lastException != null) {
                            throw lastException;
                        }
                        // unable to get user information, throw exception
                        if (responseBody == null) {
                            throw new InternalAuthenticationServiceException("Unable to find user information");
                        }
                        // else connect the user
                        return createUser( new JsonObject(responseBody).getMap());
                    })
                    .onErrorResumeNext(ex -> {
                        if (ex instanceof AuthenticationException) {
                            return Maybe.error(ex);
                        }
                        LOGGER.error("An error has occurred while calling the remote HTTP identity provider {}", ex);
                        return Maybe.error(new InternalAuthenticationServiceException("An error has occurred while calling the remote HTTP identity provider", ex));
                    });
        } catch (Exception ex) {
            LOGGER.error("An error has occurred while authenticating the user {}", ex);
            return Maybe.error(new InternalAuthenticationServiceException("An error has occurred while authenticating the user", ex));
        }
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return Maybe.empty();
    }

    private User createUser(Map<String, Object> attributes) {
        // apply user mapping
        Map<String, Object> mappedAttributes = applyUserMapping(attributes);

        // sub claim is required
        if (mappedAttributes.isEmpty() || mappedAttributes.get(StandardClaims.SUB) == null) {
            throw new InternalAuthenticationServiceException("The 'sub' claim for the user is required");
        }

        // apply role mapping
        List<String> roles = applyRoleMapping(attributes);

        // create the user
        String username = mappedAttributes.getOrDefault(StandardClaims.PREFERRED_USERNAME, attributes.get(StandardClaims.SUB)).toString();
        DefaultUser user = new DefaultUser(username);
        user.setId(mappedAttributes.get(StandardClaims.SUB).toString());
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.putAll(mappedAttributes);
        // update username if user mapping has been changed
        if (additionalInformation.containsKey(StandardClaims.PREFERRED_USERNAME)) {
            user.setUsername(additionalInformation.get(StandardClaims.PREFERRED_USERNAME).toString());
        }
        user.setAdditionalInformation(additionalInformation);
        // set user roles
        user.setRoles(roles);
        return user;
    }

    private Map<String, Object> applyUserMapping(Map<String, Object> attributes) {
        if (!mappingEnabled()) {
            return attributes;
        }

        Map<String, Object> claims = new HashMap<>();
        this.mapper.getMappers().forEach((k, v) -> {
            if (attributes.containsKey(v)) {
                claims.put(k, attributes.get(v));
            }
        });
        return claims;
    }

    private List<String> applyRoleMapping(Map<String, Object> attributes) {
        if (!roleMappingEnabled()) {
            return Collections.emptyList();
        }

        Set<String> roles = new HashSet<>();
        roleMapper.getRoles().forEach((role, users) -> {
            Arrays.asList(users).forEach(u -> {
                // role mapping have the following syntax userAttribute=userValue
                String[] roleMapping = u.split("=",2);
                String userAttribute = roleMapping[0];
                String userValue = roleMapping[1];
                if (attributes.containsKey(userAttribute)) {
                    Object attribute = attributes.get(userAttribute);
                    // attribute is a list
                    if (attribute instanceof Collection && ((Collection) attribute).contains(userValue)) {
                        roles.add(role);
                    } else if (userValue.equals(attributes.get(userAttribute))) {
                        roles.add(role);
                    }
                }
            });
        });

        return new ArrayList<>(roles);
    }

    private boolean mappingEnabled() {
        return this.mapper != null && this.mapper.getMappers() != null && !this.mapper.getMappers().isEmpty();
    }

    private boolean roleMappingEnabled() {
        return this.roleMapper != null && this.roleMapper.getRoles() != null && !this.roleMapper.getRoles().isEmpty();
    }

    private static Map<String, String> format(String query) {
        Map<String, String> queryPairs = new LinkedHashMap<String, String>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            queryPairs.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return queryPairs;
    }
}

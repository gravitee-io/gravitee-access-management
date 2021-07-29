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
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.http.HttpIdentityProviderResponse;
import io.gravitee.am.identityprovider.http.authentication.spring.HttpAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.http.configuration.HttpAuthResourcePathsConfiguration;
import io.gravitee.am.identityprovider.http.configuration.HttpIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.http.configuration.HttpResourceConfiguration;
import io.gravitee.am.identityprovider.http.configuration.HttpResponseErrorCondition;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.common.http.HttpHeader;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.gravitee.el.TemplateEngine;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private static final String USER_CONTEXT_KEY = "user";

    @Autowired
    @Qualifier("idpHttpAuthWebClient")
    private WebClient client;

    @Autowired
    private HttpIdentityProviderConfiguration configuration;

    @Autowired
    private IdentityProviderMapper mapper;

    @Autowired
    private IdentityProviderRoleMapper roleMapper;

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
            final Single<HttpResponse<Buffer>> requestHandler = processRequest(templateEngine, authenticationURI, authenticationHttpMethod, authenticationHttpHeaders, authenticationBody);

            return requestHandler
                    .toMaybe()
                    .map(httpResponse -> {
                        final List<HttpResponseErrorCondition> errorConditions = resourceConfiguration.getHttpResponseErrorConditions();
                        Map<String, Object> userAttributes = processResponse(templateEngine, errorConditions, httpResponse);
                        return createUser(authentication.getContext(), userAttributes);
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
    public Maybe<User> loadPreAuthenticatedUser(Authentication authentication) {
        return loadByUsername0(authentication.getContext(), new DefaultUser((io.gravitee.am.model.User) authentication.getPrincipal()));
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return loadByUsername0(new SimpleAuthenticationContext(), new DefaultUser(username));
    }

    private Maybe<User> loadByUsername0(AuthenticationContext authenticationContext, User user) {
        // prepare request
        final HttpAuthResourcePathsConfiguration authResourceConfiguration = configuration.getAuthenticationResource().getPaths();
        if (authResourceConfiguration == null) {
            return Maybe.empty();
        }
        if (authResourceConfiguration.getLoadPreAuthUserResource() == null) {
            return Maybe.empty();
        }

        final HttpResourceConfiguration readResourceConfiguration = authResourceConfiguration.getLoadPreAuthUserResource();

        if (readResourceConfiguration.getBaseURL() == null) {
            LOGGER.warn("Missing pre-authenticated user resource base URL");
            return Maybe.empty();
        }

        if (readResourceConfiguration.getHttpMethod() == null) {
            LOGGER.warn("Missing pre-authenticated user resource HTTP method");
            return Maybe.empty();
        }

        try {
            // prepare context
            TemplateEngine templateEngine = authenticationContext.getTemplateEngine();
            templateEngine.getTemplateContext().setVariable(USER_CONTEXT_KEY, user);

            // prepare request
            final String readUserURI = readResourceConfiguration.getBaseURL();
            final HttpMethod readUserHttpMethod = HttpMethod.valueOf(readResourceConfiguration.getHttpMethod().toString());
            final List<HttpHeader> readUserHttpHeaders = readResourceConfiguration.getHttpHeaders();
            final String readUserBody = readResourceConfiguration.getHttpBody();
            final Single<HttpResponse<Buffer>> requestHandler = processRequest(templateEngine, readUserURI, readUserHttpMethod, readUserHttpHeaders, readUserBody);

            return requestHandler
                    .toMaybe()
                    .map(httpResponse -> {
                        final List<HttpResponseErrorCondition> errorConditions = readResourceConfiguration.getHttpResponseErrorConditions();
                        Map<String, Object> userAttributes = processResponse(templateEngine, errorConditions, httpResponse);
                        return createUser(authenticationContext, userAttributes);
                    })
                    .onErrorResumeNext(ex -> {
                        if (ex instanceof AbstractManagementException) {
                            return Maybe.error(ex);
                        }
                        LOGGER.error("An error has occurred when loading pre-authenticated user {} from the remote HTTP identity provider", user.getUsername() != null ? user.getUsername() : user.getEmail(), ex);
                        return Maybe.error(new TechnicalManagementException("An error has occurred when loading pre-authenticated user from the remote HTTP identity provider", ex));
                    });
        } catch (Exception ex) {
            LOGGER.error("An error has occurred when loading pre-authenticated user {}", user.getUsername() != null ? user.getUsername() : user.getEmail(), ex);
            return Maybe.error(new TechnicalManagementException("An error has occurred when when loading pre-authenticated user", ex));
        }
    }

    private Single<HttpResponse<Buffer>> processRequest(TemplateEngine templateEngine,
                                                        String httpURI,
                                                        HttpMethod httpMethod,
                                                        List<HttpHeader> httpHeaders,
                                                        String httpBody) {
        // prepare request
        final String evaluatedHttpURI = templateEngine.getValue(httpURI, String.class);
        final HttpRequest<Buffer> httpRequest = client.requestAbs(httpMethod, evaluatedHttpURI);

        // set headers
        if (httpHeaders != null) {
            httpHeaders.forEach(header -> {
                String extValue = templateEngine.getValue(header.getValue(), String.class);
                httpRequest.putHeader(header.getName(), extValue);
            });
        }

        // set body
        Single<HttpResponse<Buffer>> responseHandler;
        if (httpBody != null && !httpBody.isEmpty()) {
            String bodyRequest = templateEngine.getValue(httpBody, String.class);
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
        return responseHandler;
    }

    private Map<String, Object> processResponse(TemplateEngine templateEngine, List<HttpResponseErrorCondition> errorConditions, HttpResponse<Buffer> httpResponse) throws Exception {
        String responseBody =  httpResponse.bodyAsString();
        templateEngine.getTemplateContext().setVariable(AUTHENTICATION_RESPONSE_CONTEXT_KEY, new HttpIdentityProviderResponse(httpResponse, responseBody));

        // process response
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

        // if remote API call failed, throw exception
        if (lastException != null) {
            throw lastException;
        }
        if (responseBody == null) {
            throw new InternalAuthenticationServiceException("Unable to find user information");
        }
        return responseBody.startsWith("[") ?
                new JsonArray(responseBody).getJsonObject(0).getMap() : new JsonObject(responseBody).getMap();
    }

    private User createUser(AuthenticationContext authContext, Map<String, Object> attributes) {
        // apply user mapping
        Map<String, Object> mappedAttributes = applyUserMapping(authContext, attributes);

        // sub claim is required
        if (mappedAttributes.isEmpty() || mappedAttributes.get(StandardClaims.SUB) == null) {
            throw new InternalAuthenticationServiceException("The 'sub' claim for the user is required");
        }

        // apply role mapping
        List<String> roles = applyRoleMapping(authContext, attributes);

        // create the user
        String username = mappedAttributes.getOrDefault(StandardClaims.PREFERRED_USERNAME, mappedAttributes.get(StandardClaims.SUB)).toString();
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

    private Map<String, Object> applyUserMapping(AuthenticationContext authContext, Map<String, Object> attributes) {
        if (!mappingEnabled()) {
            return attributes;
        }
        return this.mapper.apply(authContext, attributes);
    }

    private List<String> applyRoleMapping(AuthenticationContext authContext, Map<String, Object> attributes) {
        if (!roleMappingEnabled()) {
            return Collections.emptyList();
        }
        return this.roleMapper.apply(authContext, attributes);
    }

    private boolean mappingEnabled() {
        return this.mapper != null;
    }

    private boolean roleMappingEnabled() {
        return this.roleMapper != null;
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

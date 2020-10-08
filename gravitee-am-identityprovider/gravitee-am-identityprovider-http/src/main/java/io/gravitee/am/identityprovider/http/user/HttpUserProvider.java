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
package io.gravitee.am.identityprovider.http.user;

import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.http.HttpIdentityProviderResponse;
import io.gravitee.am.identityprovider.http.configuration.HttpIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.http.configuration.HttpResourceConfiguration;
import io.gravitee.am.identityprovider.http.configuration.HttpResponseErrorCondition;
import io.gravitee.am.identityprovider.http.configuration.HttpUsersResourceConfiguration;
import io.gravitee.am.identityprovider.http.user.spring.HttpUserProviderConfiguration;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.common.http.HttpHeader;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.gravitee.el.TemplateEngine;
import io.reactivex.Completable;
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
import org.springframework.context.annotation.Import;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import({HttpUserProviderConfiguration.class})
public class HttpUserProvider implements UserProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpUserProvider.class);
    private static final String USER_CONTEXT_KEY = "user";
    private static final String USER_API_RESPONSE_CONTEXT_KEY = "usersResponse";

    @Autowired
    private WebClient client;

    @Autowired
    private HttpIdentityProviderConfiguration configuration;

    @Override
    public Maybe<User> findByEmail(String email) {
        // prepare request
        final HttpUsersResourceConfiguration usersResourceConfiguration = configuration.getUsersResource();
        final HttpResourceConfiguration readResourceConfiguration = usersResourceConfiguration.getPaths().getReadResourceByEmail();
        final DefaultUser user = new DefaultUser(null);
        user.setEmail(email);

        return findByUser(usersResourceConfiguration, readResourceConfiguration, user);
    }

    @Override
    public Maybe<User> findByUsername(String username) {
        // prepare request
        final HttpUsersResourceConfiguration usersResourceConfiguration = configuration.getUsersResource();
        final HttpResourceConfiguration readResourceConfiguration = usersResourceConfiguration.getPaths().getReadResource();
        final DefaultUser user = new DefaultUser(username);

        return findByUser(usersResourceConfiguration, readResourceConfiguration, user);
    }

    @Override
    public Single<User> create(User user) {
        try {
            // prepare context
            AuthenticationContext authenticationContext = new SimpleAuthenticationContext();
            TemplateEngine templateEngine = authenticationContext.getTemplateEngine();
            templateEngine.getTemplateContext().setVariable(USER_CONTEXT_KEY, user);

            // prepare request
            final HttpUsersResourceConfiguration usersResourceConfiguration = configuration.getUsersResource();
            final HttpResourceConfiguration createResourceConfiguration = usersResourceConfiguration.getPaths().getCreateResource();
            final String createUserURI = usersResourceConfiguration.getBaseURL() + createResourceConfiguration.getBaseURL();
            final HttpMethod createUserHttpMethod = HttpMethod.valueOf(createResourceConfiguration.getHttpMethod().toString());
            final List<HttpHeader> createUserHttpHeaders = createResourceConfiguration.getHttpHeaders();
            final String createUserBody = createResourceConfiguration.getHttpBody();
            final Single<HttpResponse<Buffer>> requestHandler = processRequest(templateEngine, createUserURI, createUserHttpMethod, createUserHttpHeaders, createUserBody);

            return requestHandler
                    .map(httpResponse -> {
                        final List<HttpResponseErrorCondition> errorConditions = createResourceConfiguration.getHttpResponseErrorConditions();
                        Map<String, Object> userAttributes = processResponse(templateEngine, errorConditions, httpResponse);
                        return convert(user.getUsername(), userAttributes);
                    })
                    .onErrorResumeNext(ex -> {
                        if (ex instanceof AbstractManagementException) {
                            return Single.error(ex);
                        }
                        LOGGER.error("An error has occurred while creating user {} from the remote HTTP identity provider", user.getUsername(), ex);
                        return Single.error(new TechnicalManagementException("An error has occurred while creating user from the remote HTTP identity provider", ex));
                    });
        } catch (Exception ex) {
            LOGGER.error("An error has occurred while creating the user {}", user.getUsername(), ex);
            return Single.error(new TechnicalManagementException("An error has occurred while creating the user", ex));
        }
    }

    @Override
    public Single<User> update(String id, User updateUser) {
        try {
            // prepare context
            AuthenticationContext authenticationContext = new SimpleAuthenticationContext();
            TemplateEngine templateEngine = authenticationContext.getTemplateEngine();
            ((DefaultUser) updateUser).setId(id);
            templateEngine.getTemplateContext().setVariable(USER_CONTEXT_KEY, updateUser);

            // prepare request
            final HttpUsersResourceConfiguration usersResourceConfiguration = configuration.getUsersResource();
            final HttpResourceConfiguration updateResourceConfiguration = usersResourceConfiguration.getPaths().getUpdateResource();
            final String updateUserURI = usersResourceConfiguration.getBaseURL() + updateResourceConfiguration.getBaseURL();
            final HttpMethod updateUserHttpMethod = HttpMethod.valueOf(updateResourceConfiguration.getHttpMethod().toString());
            final List<HttpHeader> updateUserHttpHeaders = updateResourceConfiguration.getHttpHeaders();
            final String updateUserBody = updateResourceConfiguration.getHttpBody();
            final Single<HttpResponse<Buffer>> requestHandler = processRequest(templateEngine, updateUserURI, updateUserHttpMethod, updateUserHttpHeaders, updateUserBody);

            return requestHandler
                    .map(httpResponse -> {
                        final List<HttpResponseErrorCondition> errorConditions = updateResourceConfiguration.getHttpResponseErrorConditions();
                        Map<String, Object> userAttributes = processResponse(templateEngine, errorConditions, httpResponse);
                        return convert(updateUser.getUsername(), userAttributes);
                    })
                    .onErrorResumeNext(ex -> {
                        if (ex instanceof AbstractManagementException) {
                            return Single.error(ex);
                        }
                        LOGGER.error("An error has occurred while updating user {} from the remote HTTP identity provider", updateUser.getUsername(), ex);
                        return Single.error(new TechnicalManagementException("An error has occurred while updating user from the remote HTTP identity provider", ex));
                    });
        } catch (Exception ex) {
            LOGGER.error("An error has occurred while updating the user {}", updateUser.getUsername(), ex);
            return Single.error(new TechnicalManagementException("An error has occurred while updating the user", ex));
        }
    }

    @Override
    public Completable delete(String id) {
        try {
            // prepare context
            DefaultUser deleteUser = new DefaultUser(null);
            deleteUser.setId(id);
            AuthenticationContext authenticationContext = new SimpleAuthenticationContext();
            TemplateEngine templateEngine = authenticationContext.getTemplateEngine();
            templateEngine.getTemplateContext().setVariable(USER_CONTEXT_KEY, deleteUser);

            // prepare request
            final HttpUsersResourceConfiguration usersResourceConfiguration = configuration.getUsersResource();
            final HttpResourceConfiguration deleteResourceConfiguration = usersResourceConfiguration.getPaths().getDeleteResource();
            final String deleteUserURI = usersResourceConfiguration.getBaseURL() + deleteResourceConfiguration.getBaseURL();
            final HttpMethod deleteUserHttpMethod = HttpMethod.valueOf(deleteResourceConfiguration.getHttpMethod().toString());
            final List<HttpHeader> deleteUserHttpHeaders = deleteResourceConfiguration.getHttpHeaders();
            final String updateUserBody = deleteResourceConfiguration.getHttpBody();
            final Single<HttpResponse<Buffer>> requestHandler = processRequest(templateEngine, deleteUserURI, deleteUserHttpMethod, deleteUserHttpHeaders, updateUserBody);

            return requestHandler
                    .flatMapCompletable(httpResponse -> {
                        final List<HttpResponseErrorCondition> errorConditions = deleteResourceConfiguration.getHttpResponseErrorConditions();
                        try {
                            processResponse(templateEngine, errorConditions, httpResponse);
                            return Completable.complete();
                        } catch (Exception ex) {
                            return Completable.error(ex);
                        }
                    })
                    .onErrorResumeNext(ex -> {
                        if (ex instanceof AbstractManagementException) {
                            return Completable.error(ex);
                        }
                        LOGGER.error("An error has occurred while deleting user {} from the remote HTTP identity provider", id, ex);
                        return Completable.error(new TechnicalManagementException("An error has occurred while deleting user from the remote HTTP identity provider", ex));
                    });
        } catch (Exception ex) {
            LOGGER.error("An error has occurred while deleting the user {}", id, ex);
            return Completable.error(new TechnicalManagementException("An error has occurred while deleting the user", ex));
        }
    }

    private Maybe<User> findByUser(HttpUsersResourceConfiguration usersResourceConfiguration,
                                   HttpResourceConfiguration readResourceConfiguration,
                                   User user) {
        try {
            // prepare context
            AuthenticationContext authenticationContext = new SimpleAuthenticationContext();
            TemplateEngine templateEngine = authenticationContext.getTemplateEngine();
            templateEngine.getTemplateContext().setVariable(USER_CONTEXT_KEY, user);

            // prepare request
            final String readUserURI = usersResourceConfiguration.getBaseURL() + readResourceConfiguration.getBaseURL();
            final HttpMethod readUserHttpMethod = HttpMethod.valueOf(readResourceConfiguration.getHttpMethod().toString());
            final List<HttpHeader> readUserHttpHeaders = readResourceConfiguration.getHttpHeaders();
            final String readUserBody = readResourceConfiguration.getHttpBody();
            final Single<HttpResponse<Buffer>> requestHandler = processRequest(templateEngine, readUserURI, readUserHttpMethod, readUserHttpHeaders, readUserBody);

            return requestHandler
                    .toMaybe()
                    .map(httpResponse -> {
                        final List<HttpResponseErrorCondition> errorConditions = readResourceConfiguration.getHttpResponseErrorConditions();
                        Map<String, Object> userAttributes = processResponse(templateEngine, errorConditions, httpResponse);
                        return convert(user.getUsername(), userAttributes);
                    })
                    .onErrorResumeNext(ex -> {
                        if (ex instanceof AbstractManagementException) {
                            return Maybe.error(ex);
                        }
                        LOGGER.error("An error has occurred while searching user {} from the remote HTTP identity provider", user.getUsername() != null ? user.getUsername() : user.getEmail(), ex);
                        return Maybe.error(new TechnicalManagementException("An error has occurred while searching user from the remote HTTP identity provider", ex));
                    });
        } catch (Exception ex) {
            LOGGER.error("An error has occurred while searching the user {}", user.getUsername() != null ? user.getUsername() : user.getEmail(), ex);
            return Maybe.error(new TechnicalManagementException("An error has occurred while searching the user", ex));
        }
    }

    private User convert(String username, Map<String, Object> userAttributes) {
        final String identifierAttribute = configuration.getUsersResource().getIdentifierAttribute();
        final String usernameAttribute = configuration.getUsersResource().getUsernameAttribute();
        final String id = (String) userAttributes.get(identifierAttribute);
        final String usernameValue = (username != null) ? username : (userAttributes.get(usernameAttribute) != null) ? (String) userAttributes.get(usernameAttribute) : id;
        DefaultUser user = new DefaultUser(usernameValue);
        // set external id
        user.setId(id);
        // remove sensitive value if any
        userAttributes.remove(identifierAttribute);
        userAttributes.remove(usernameAttribute);
        userAttributes.remove("password");
        Map<String, Object> claims = new HashMap<>();
        userAttributes.forEach((k, v) -> claims.put(k, v));
        user.setAdditionalInformation(claims);
        return user;
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
        templateEngine.getTemplateContext().setVariable(USER_API_RESPONSE_CONTEXT_KEY, new HttpIdentityProviderResponse(httpResponse, responseBody));

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
            return Collections.emptyMap();
        }
        return responseBody.startsWith("[") ?
                new JsonArray(responseBody).getJsonObject(0).getMap() : new JsonObject(responseBody).getMap();
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

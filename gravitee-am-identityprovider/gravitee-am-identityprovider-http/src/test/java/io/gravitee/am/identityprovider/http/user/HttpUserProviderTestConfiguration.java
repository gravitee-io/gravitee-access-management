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

import io.gravitee.am.identityprovider.api.DefaultIdentityProviderMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.identityprovider.http.configuration.*;
import io.gravitee.common.http.HttpHeader;
import io.gravitee.common.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class HttpUserProviderTestConfiguration {

    @Bean
    public HttpIdentityProviderConfiguration httpIdentityProviderConfiguration() {
        HttpIdentityProviderConfiguration configuration = new HttpIdentityProviderConfiguration();

        HttpResourceConfiguration createResource = new HttpResourceConfiguration();
        createResource.setBaseURL("/users");
        createResource.setHttpMethod(HttpMethod.POST);
        HttpHeader createHttpHeader = new HttpHeader();
        createHttpHeader.setName("Content-Type");
        createHttpHeader.setValue("application/json");
        createResource.setHttpHeaders(Collections.singletonList(createHttpHeader));
        JsonObject createJsonObject = new JsonObject();
        createJsonObject.put("username", "{#user.username}");
        createResource.setHttpBody(createJsonObject.encode());
        HttpResponseErrorCondition createErrorCondition = new HttpResponseErrorCondition();
        createErrorCondition.setValue("{#usersResponse.status == 400}");
        createErrorCondition.setException("io.gravitee.am.service.exception.UserAlreadyExistsException");
        createResource.setHttpResponseErrorConditions(Arrays.asList(createErrorCondition));

        HttpResourceConfiguration readResource = new HttpResourceConfiguration();
        readResource.setBaseURL("/users?username={#user.username}");
        readResource.setHttpMethod(HttpMethod.GET);
        HttpResponseErrorCondition readErrorCondition = new HttpResponseErrorCondition();
        readErrorCondition.setValue("{#usersResponse.status == 404}");
        readErrorCondition.setException("io.gravitee.am.service.exception.UserNotFoundException");
        readResource.setHttpResponseErrorConditions(Arrays.asList(readErrorCondition));

        HttpResourceConfiguration readByEmailResource = new HttpResourceConfiguration();
        readByEmailResource.setBaseURL("/users?email={#user.email}");
        readByEmailResource.setHttpMethod(HttpMethod.GET);
        HttpResponseErrorCondition readByEmailErrorCondition = new HttpResponseErrorCondition();
        readByEmailErrorCondition.setValue("{#usersResponse.status == 404}");
        readByEmailErrorCondition.setException("io.gravitee.am.service.exception.UserNotFoundException");
        readByEmailResource.setHttpResponseErrorConditions(Arrays.asList(readByEmailErrorCondition));

        HttpResourceConfiguration updateResource = new HttpResourceConfiguration();
        updateResource.setBaseURL("/users/{#user.id}");
        updateResource.setHttpMethod(HttpMethod.PUT);
        HttpHeader updateHttpHeader = new HttpHeader();
        updateHttpHeader.setName("Content-Type");
        updateHttpHeader.setValue("application/json");
        updateResource.setHttpHeaders(Collections.singletonList(updateHttpHeader));
        JsonObject updateJsonObject = new JsonObject();
        createJsonObject.put("username", "{#user.username}");
        updateResource.setHttpBody(updateJsonObject.encode());
        HttpResponseErrorCondition updateErrorCondition = new HttpResponseErrorCondition();
        updateErrorCondition.setValue("{#usersResponse.status == 404}");
        updateErrorCondition.setException("io.gravitee.am.service.exception.UserNotFoundException");
        updateResource.setHttpResponseErrorConditions(Arrays.asList(updateErrorCondition));

        HttpResourceConfiguration deleteResource = new HttpResourceConfiguration();
        deleteResource.setBaseURL("/users/{#user.id}");
        deleteResource.setHttpMethod(HttpMethod.DELETE);
        HttpResponseErrorCondition deleteErrorCondition = new HttpResponseErrorCondition();
        deleteErrorCondition.setValue("{#usersResponse.status == 404}");
        deleteErrorCondition.setException("io.gravitee.am.service.exception.UserNotFoundException");
        deleteResource.setHttpResponseErrorConditions(Arrays.asList(deleteErrorCondition));

        HttpUsersResourceConfiguration usersResourceConfiguration = new HttpUsersResourceConfiguration();
        usersResourceConfiguration.setBaseURL("http://localhost:19998/api");
        usersResourceConfiguration.setIdentifierAttribute("id");
        usersResourceConfiguration.setApplyUserMapper(true);

        HttpUsersResourcePathsConfiguration pathsConfiguration = new HttpUsersResourcePathsConfiguration();
        pathsConfiguration.setCreateResource(createResource);
        pathsConfiguration.setReadResource(readResource);
        pathsConfiguration.setReadResourceByEmail(readByEmailResource);
        pathsConfiguration.setUpdateResource(updateResource);
        pathsConfiguration.setDeleteResource(deleteResource);
        usersResourceConfiguration.setPaths(pathsConfiguration);
        configuration.setUsersResource(usersResourceConfiguration);

        return configuration;
    }

    @Bean
    public UserProvider userProvider() {
        return new HttpUserProvider();
    }

    @Bean
    public IdentityProviderMapper mapper() {
        return new DefaultIdentityProviderMapper();
    }

    @Bean
    public Vertx vertx() {
        return Vertx.vertx();
    }
}

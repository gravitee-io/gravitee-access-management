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
package io.gravitee.am.gateway.handler.scim.spring;

import io.gravitee.am.gateway.handler.api.ProtocolConfiguration;
import io.gravitee.am.gateway.handler.scim.resources.bulk.BulkEndpointConfiguration;
import io.gravitee.am.gateway.handler.scim.service.BulkService;
import io.gravitee.am.gateway.handler.scim.service.GroupService;
import io.gravitee.am.gateway.handler.scim.service.ServiceProviderConfigService;
import io.gravitee.am.gateway.handler.scim.service.UserService;
import io.gravitee.am.gateway.handler.scim.service.impl.BulkServiceImpl;
import io.gravitee.am.gateway.handler.scim.service.impl.GroupServiceImpl;
import io.gravitee.am.gateway.handler.scim.service.impl.ServiceProviderConfigServiceImpl;
import io.gravitee.am.gateway.handler.scim.service.impl.UserServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.authentication.crypto.password.Argon2IdPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class SCIMConfiguration implements ProtocolConfiguration {

    @Bean
    public UserService userService() {
        return new UserServiceImpl();
    }

    @Bean
    public GroupService groupService() {
        return new GroupServiceImpl();
    }

    @Bean
    public BulkService bulkService(UserService userService, Domain domain) {
        return new BulkServiceImpl(userService, domain);
    }

    @Bean
    public ServiceProviderConfigService serviceProviderConfigService() {
        return new ServiceProviderConfigServiceImpl();
    }

    @Bean
    public PasswordEncoder argon2IdEncoder(){
        return new Argon2IdPasswordEncoder();
    }

    @Bean
    public BulkEndpointConfiguration bulkEndpointConfiguration(
            @Value("${handlers.scim.bulk.maxRequestLength:1048576}") int bulkMaxRequestLength,
            @Value("${handlers.scim.bulk.maxRequestOperations:1000}") int bulkMaxRequestOperations){
        return BulkEndpointConfiguration.builder()
                .bulkMaxRequestLength(bulkMaxRequestLength)
                .bulkMaxRequestOperations(bulkMaxRequestOperations)
                .build();
    }
}

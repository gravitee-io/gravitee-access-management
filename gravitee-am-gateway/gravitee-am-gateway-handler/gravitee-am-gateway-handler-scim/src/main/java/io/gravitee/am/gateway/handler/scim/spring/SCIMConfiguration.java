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
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.common.email.EmailStagingProcessor;
import io.gravitee.am.gateway.handler.common.email.EmailStagingService;
import io.gravitee.am.gateway.handler.scim.resources.bulk.BulkEndpointConfiguration;
import io.gravitee.am.gateway.handler.scim.service.BulkService;
import io.gravitee.am.gateway.handler.scim.service.ProvisioningUserService;
import io.gravitee.am.gateway.handler.scim.service.ScimGroupService;
import io.gravitee.am.gateway.handler.scim.service.ServiceProviderConfigService;
import io.gravitee.am.gateway.handler.scim.service.impl.BulkServiceImpl;
import io.gravitee.am.gateway.handler.scim.service.impl.ProvisioningUserServiceImpl;
import io.gravitee.am.gateway.handler.scim.service.impl.ScimGroupServiceImpl;
import io.gravitee.am.gateway.handler.scim.service.impl.ServiceProviderConfigServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.ApplicationService;
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
    public ProvisioningUserService userService() {
        return new ProvisioningUserServiceImpl();
    }

    @Bean
    public EmailStagingProcessor emailStagingProcessor(
            EmailStagingService emailStagingService,
            EmailService emailService,
            DataPlaneRegistry dataPlaneRegistry,
            ApplicationService applicationService,
            Domain domain,
            @Value("${email.enabled:false}") boolean emailEnabled,
            @Value("${email.bulk.enabled:false}") boolean bulkEnabled,
            @Value("${email.bulk.batch:" + EmailStagingProcessor.DEFAULT_BATCH_SIZE + "}") int batchSize,
            @Value("${email.bulk.period:" + EmailStagingProcessor.DEFAULT_PERIOD_IN_SECONDS + "}") int batchPeriod,
            @Value("${email.bulk.attempts:" + EmailStagingProcessor.DEFAULT_MAX_ATTEMPTS + "}") int maxAttempts) {
        return new EmailStagingProcessor(
                emailStagingService,
                emailService,
                dataPlaneRegistry,
                applicationService,
                domain,
                batchSize,
                batchPeriod,
                maxAttempts,
                emailEnabled && bulkEnabled);
    }

    @Bean
    public ScimGroupService groupService() {
        return new ScimGroupServiceImpl();
    }

    @Bean
    public BulkService bulkService(ProvisioningUserService userService, Domain domain,
                                   @Value("${handlers.scim.bulk.maxConcurrency:1}") int bulkMaxConcurrency) {
        return new BulkServiceImpl(userService, domain, bulkMaxConcurrency);
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

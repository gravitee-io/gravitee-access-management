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
package io.gravitee.am.management.handlers.management.api.spring;

import io.gravitee.am.management.handlers.management.api.adapter.ScopeApprovalAdapter;
import io.gravitee.am.management.handlers.management.api.adapter.ScopeApprovalAdapterImpl;
import io.gravitee.am.management.handlers.management.api.adapter.UMAResourceManagementAdapter;
import io.gravitee.am.management.handlers.management.api.adapter.UMAResourceManagementAdapterImpl;
import io.gravitee.am.management.handlers.management.api.authentication.manager.form.FormManager;
import io.gravitee.am.management.handlers.management.api.authentication.manager.form.impl.FormManagerImpl;
import io.gravitee.am.management.handlers.management.api.authentication.service.AuthenticationService;
import io.gravitee.am.management.handlers.management.api.authentication.service.impl.AuthenticationServiceImpl;
import io.gravitee.am.management.handlers.management.api.preview.PreviewService;
import io.gravitee.am.management.handlers.management.api.spring.security.SecurityConfiguration;
import io.gravitee.am.management.handlers.management.api.spring.security.WebMvcConfiguration;
import io.gravitee.am.management.service.RevokeTokenManagementService;
import io.gravitee.am.management.service.dataplane.UMAResourceManagementService;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.ScopeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@ComponentScan({"io.gravitee.am.management.handlers.management.api.resources.enhancer"})
@Import({WebMvcConfiguration.class, SecurityConfiguration.class})
public class ManagementConfiguration {

    @Bean
    public AuthenticationService authenticationService() {
        return new AuthenticationServiceImpl();
    }

    @Bean
    public FormManager formManager() {
        return new FormManagerImpl();
    }

    @Bean
    public PreviewService previewService() {
        return new PreviewService();
    }

    @Bean
    ScopeApprovalAdapter scopeApprovalAdapter(ScopeApprovalService scopeApprovalService,
        RevokeTokenManagementService revokeTokenManagementService,
        ApplicationService applicationService,
        ScopeService scopeService,
        DataPlaneRegistry dataPlaneRegistry) {
        return new ScopeApprovalAdapterImpl(scopeApprovalService, revokeTokenManagementService, applicationService, scopeService, dataPlaneRegistry);
    }

    @Bean
    UMAResourceManagementAdapter umaResourceManagementAdapter(UMAResourceManagementService umaResourceManagementService,
                                                              DataPlaneRegistry dataPlaneRegistry) {
        return new UMAResourceManagementAdapterImpl(umaResourceManagementService, dataPlaneRegistry);
    }

    @Bean
    public UserBulkConfiguration bulkEndpointConfiguration(
            @Value("${user.bulk.maxRequestLength:1048576}") int bulkMaxRequestLength,
            @Value("${user.bulk.maxRequestOperations:1000}") int bulkMaxRequestOperations){
        return UserBulkConfiguration.builder()
                .bulkMaxRequestLength(bulkMaxRequestLength)
                .bulkMaxRequestOperations(bulkMaxRequestOperations)
                .build();
    }
}

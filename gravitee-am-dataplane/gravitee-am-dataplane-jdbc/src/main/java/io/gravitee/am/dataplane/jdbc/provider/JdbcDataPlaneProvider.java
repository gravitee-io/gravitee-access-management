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
package io.gravitee.am.dataplane.jdbc.provider;

import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.am.dataplane.api.repository.AccessPolicyRepository;
import io.gravitee.am.dataplane.api.repository.CredentialRepository;
import io.gravitee.am.dataplane.api.repository.CertificateCredentialRepository;
import io.gravitee.am.dataplane.api.repository.DeviceRepository;
import io.gravitee.am.dataplane.api.repository.GroupRepository;
import io.gravitee.am.dataplane.api.repository.LoginAttemptRepository;
import io.gravitee.am.dataplane.api.repository.PasswordHistoryRepository;
import io.gravitee.am.dataplane.api.repository.PermissionTicketRepository;
import io.gravitee.am.dataplane.api.repository.ResourceRepository;
import io.gravitee.am.dataplane.api.repository.ScopeApprovalRepository;
import io.gravitee.am.dataplane.api.repository.UserActivityRepository;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.dataplane.jdbc.spring.JdbcDataPlaneSpringConfiguration;
import io.gravitee.am.repository.jdbc.provider.impl.R2DBCPoolWrapper;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import io.r2dbc.spi.ConnectionFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;

@Slf4j
@Import({JdbcDataPlaneSpringConfiguration.class})
@Getter
public class JdbcDataPlaneProvider implements DataPlaneProvider, InitializingBean {

    @Autowired
    private DataPlaneDescription dataPlaneDescription;

    @Autowired
    private R2DBCPoolWrapper clientWrapper;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private CertificateCredentialRepository certificateCredentialRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private ScopeApprovalRepository scopeApprovalRepository;

    @Autowired
    private UserActivityRepository userActivityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHistoryRepository passwordHistoryRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private AccessPolicyRepository accessPolicyRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private PermissionTicketRepository permissionTicketRepository;

    @Autowired
    @Qualifier("dataplaneUpgraderRepository")
    private UpgraderRepository upgraderRepository;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("DataPlane provider loaded with id {}", dataPlaneDescription.id());
    }

    @Override
    public void stop() {

    }

    @Override
    public boolean canHandle(String backendType) {
        return ConnectionProvider.BACKEND_TYPE_RDBMS.equals(backendType);
    }

    @Override
    public ClientWrapper<ConnectionFactory> getClientWrapper() {
        return this.clientWrapper;
    }

    @Override
    public CredentialRepository getCredentialRepository() {
        return credentialRepository;
    }

    @Override
    public CertificateCredentialRepository getCertificateCredentialRepository() {
        return certificateCredentialRepository;
    }

    @Override
    public DeviceRepository getDeviceRepository() {
        return deviceRepository;
    }

    @Override
    public GroupRepository getGroupRepository() {
        return groupRepository;
    }

    @Override
    public ScopeApprovalRepository getScopeApprovalRepository() {
        return scopeApprovalRepository;
    }

    @Override
    public UserActivityRepository getUserActivityRepository() {
        return userActivityRepository;
    }

    @Override
    public UserRepository getUserRepository() {
        return userRepository;
    }

    @Override
    public PasswordHistoryRepository getPasswordHistoryRepository() {
        return passwordHistoryRepository;
    }

    @Override
    public LoginAttemptRepository getLoginAttemptRepository() {
        return loginAttemptRepository;
    }

    @Override
    public AccessPolicyRepository getAccessPolicyRepository() {
        return accessPolicyRepository;
    }

    @Override
    public ResourceRepository getResourceRepository() {
        return resourceRepository;
    }

    @Override
    public PermissionTicketRepository getPermissionTicketRepository() {
        return permissionTicketRepository;
    }
}

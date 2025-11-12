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
package io.gravitee.am.dataplane.api;

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
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.node.api.upgrader.UpgraderRepository;

public interface DataPlaneProvider {

    void stop();

    DataPlaneDescription getDataPlaneDescription();

    CredentialRepository getCredentialRepository();

    CertificateCredentialRepository getCertificateCredentialRepository();

    DeviceRepository getDeviceRepository();

    GroupRepository getGroupRepository();

    ScopeApprovalRepository getScopeApprovalRepository();

    UserActivityRepository getUserActivityRepository();

    UserRepository getUserRepository();

    PasswordHistoryRepository getPasswordHistoryRepository();

    LoginAttemptRepository getLoginAttemptRepository();

    AccessPolicyRepository getAccessPolicyRepository();

    ResourceRepository getResourceRepository();

    PermissionTicketRepository getPermissionTicketRepository();

    UpgraderRepository getUpgraderRepository();

    boolean canHandle(String backendType);

    <T> ClientWrapper<T> getClientWrapper();
}

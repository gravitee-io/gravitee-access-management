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
package io.gravitee.am.plugins.dataplane.core;

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
import io.gravitee.am.dataplane.exceptions.IllegalDataPlaneIdException;
import io.gravitee.am.model.Domain;
import io.gravitee.common.service.AbstractService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static io.gravitee.am.dataplane.api.DataPlaneDescription.DEFAULT_DATA_PLANE_ID;
import static org.springframework.util.StringUtils.hasText;

@Slf4j
public class DataPlaneRegistryImpl extends AbstractService<DataPlaneRegistryImpl> implements DataPlaneRegistry {

    private DataPlanePluginManager dataPlanePluginManager;

    private DataPlaneLoader dataPlaneLoader;

    private Map<String, DataPlaneProvider> dataPlanProviders = new ConcurrentHashMap<>();

    private Map<String, DataPlaneDescription> dataPlanDescriptions = new ConcurrentHashMap<>();

    public DataPlaneRegistryImpl(DataPlaneLoader dataPlaneLoader, DataPlanePluginManager dataPlanePluginManager) {
        this.dataPlaneLoader = dataPlaneLoader;
        this.dataPlanePluginManager = dataPlanePluginManager;
    }

    public List<DataPlaneDescription> getDataPlanes() {
        return dataPlanDescriptions.values().stream().toList();
    }

    @Override
    public List<DataPlaneProvider> getAllProviders() {
        return List.copyOf(dataPlanProviders.values());
    }

    @Override
    public DataPlaneProvider getProvider(Domain domain) {
        Objects.requireNonNull(domain, "Domain is required to provide DataPlane");
        final var dataPlaneId = extractDataPlaneId(domain);

        return getProviderById(dataPlaneId);
    }

    @Override
    public DataPlaneProvider getProviderById(String dataPlaneId) {
        final var provider = dataPlanProviders.get(dataPlaneId);
        if (provider == null) {
            throw new IllegalDataPlaneIdException(dataPlaneId);
        }
        return provider;
    }

    @Override
    public DataPlaneDescription getDescription(Domain domain) {
        Objects.requireNonNull(domain, "Domain is required to provide DataPlane Description");
        final var dataPlaneId = extractDataPlaneId(domain);

        final var desc = dataPlanDescriptions.get(dataPlaneId);
        if (desc == null) {
            throw new IllegalDataPlaneIdException(dataPlaneId);
        }
        return desc;
    }

    private String extractDataPlaneId(Domain domain) {
        var dataPlaneId = domain.getDataPlaneId();
        if (!hasText(dataPlaneId)) {
            log.warn("Domain '{}' has empty dataPlaneId, upgrader may have to be executed. Fallback to 'default'.", domain.getId());
            dataPlaneId = DEFAULT_DATA_PLANE_ID;
        }
        return dataPlaneId;
    }

    @Override
    public CredentialRepository getCredentialRepository(Domain domain) {
        return getProvider(domain).getCredentialRepository();
    }

    @Override
    public CertificateCredentialRepository getCertificateCredentialRepository(Domain domain) {
        return getProvider(domain).getCertificateCredentialRepository();
    }

    @Override
    public DeviceRepository getDeviceRepository(Domain domain) {
        return getProvider(domain).getDeviceRepository();
    }

    @Override
    public GroupRepository getGroupRepository(Domain domain) {
        return getProvider(domain).getGroupRepository();
    }

    @Override
    public ScopeApprovalRepository getScopeApprovalRepository(Domain domain) {
        return getProvider(domain).getScopeApprovalRepository();
    }

    @Override
    public UserActivityRepository getUserActivityRepository(Domain domain) {
        return getProvider(domain).getUserActivityRepository();
    }

    @Override
    public UserRepository getUserRepository(Domain domain) {
        return getProvider(domain).getUserRepository();
    }

    @Override
    public PasswordHistoryRepository getPasswordHistoryRepository(Domain domain) {
        return getProvider(domain).getPasswordHistoryRepository();
    }

    @Override
    public LoginAttemptRepository getLoginAttemptRepository(Domain domain) {
        return getProvider(domain).getLoginAttemptRepository();
    }

    @Override
    public AccessPolicyRepository getAccessPolicyRepository(Domain domain) {
        return getProvider(domain).getAccessPolicyRepository();
    }

    @Override
    public ResourceRepository getResourceRepository(Domain domain) {
        return getProvider(domain).getResourceRepository();
    }

    @Override
    public PermissionTicketRepository getPermissionTicketRepository(Domain domain) {
        return getProvider(domain).getPermissionTicketRepository();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        dataPlaneLoader.load(this::register);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        for(DataPlaneProvider provider : dataPlanProviders.values()) {
            provider.stop();
        }
    }

    void register(DataPlaneDescription description){
        if (!hasText(description.id())) {
            throw new IllegalStateException("Invalid data plan definition, id must be specified");
        }
        if (this.dataPlanProviders.containsKey(description.id())) {
            throw new IllegalStateException("Invalid data plan definition, id must be unique");
        }
        dataPlanePluginManager.create(description).ifPresent(provider -> this.dataPlanProviders.put(description.id(), provider));
        dataPlanDescriptions.put(description.id(), description);
    }
}

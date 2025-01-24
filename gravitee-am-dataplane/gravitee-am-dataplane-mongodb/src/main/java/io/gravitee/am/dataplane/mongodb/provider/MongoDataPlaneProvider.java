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
package io.gravitee.am.dataplane.mongodb.provider;

import com.mongodb.reactivestreams.client.MongoClient;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.am.dataplane.api.repository.CredentialRepository;
import io.gravitee.am.dataplane.api.repository.DeviceRepository;
import io.gravitee.am.dataplane.api.repository.GroupRepository;
import io.gravitee.am.dataplane.api.repository.PasswordHistoryRepository;
import io.gravitee.am.dataplane.api.repository.ScopeApprovalRepository;
import io.gravitee.am.dataplane.api.repository.UserActivityRepository;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.dataplane.mongodb.spring.MongoDataPlaneSpringConfiguration;
import io.gravitee.am.repository.provider.ClientWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Slf4j
@Import({MongoDataPlaneSpringConfiguration.class})
public class MongoDataPlaneProvider implements DataPlaneProvider, InitializingBean {

    @Autowired
    private DataPlaneDescription dataPlaneDescription;

    @Autowired
    private ClientWrapper<MongoClient> mongoClientWrapper;

    @Autowired
    private CredentialRepository credentialRepository;

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

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("DataPlane provider loaded with id {}", dataPlaneDescription.id());
    }

    @Override
    public void stop() {
        if (mongoClientWrapper != null) {
            mongoClientWrapper.releaseClient();
        }
    }

    @Override
    public CredentialRepository getCredentialRepository() {
        return credentialRepository;
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
}

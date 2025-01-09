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
import io.gravitee.am.dataplane.api.repository.CredentialRepository;
import io.gravitee.am.dataplane.api.repository.DeviceRepository;
import io.gravitee.am.dataplane.api.repository.GroupRepository;
import io.gravitee.am.dataplane.api.repository.ScopeApprovalRepository;
import io.gravitee.am.dataplane.api.repository.UserActivityRepository;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.model.Domain;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * The DataPlane Registry is an interface to access the loaded DataPlanes
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DataPlaneRegistry {
    List<DataPlaneDescription> getDataPlanes();

    Single<DataPlaneProvider> getProvider(Domain domain);

    Single<CredentialRepository> getCredentialRepository(Domain domain);

    Single<DeviceRepository> getDeviceRepository(Domain domain);

    Single<GroupRepository> getGroupRepository(Domain domain);

    Single<ScopeApprovalRepository> getScopeApprovalRepository(Domain domain);

    Single<UserActivityRepository> getUserActivityRepository(Domain domain);

    Single<UserRepository> getUserRepository(Domain domain);
}

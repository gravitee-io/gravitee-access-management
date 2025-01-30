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

package io.gravitee.am.management.service.dataplane.impl;


import io.gravitee.am.dataplane.api.search.LoginAttemptCriteria;
import io.gravitee.am.management.service.dataplane.LoginAttemptManagementService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Slf4j
public class LoginAttemptManagementServiceImpl implements LoginAttemptManagementService {

    @Autowired
    private DataPlaneRegistry dataPlaneRegistry;

    @Override
    public Completable reset(Domain domain, LoginAttemptCriteria criteria) {
        log.debug("Delete login attempt for {}", criteria);
        return dataPlaneRegistry.getLoginAttemptRepository(domain).delete(criteria)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to delete login attempt for {}", criteria, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete login attempt: %s", criteria), ex));
                });
    }
}

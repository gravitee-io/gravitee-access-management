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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.management.service.ActionLeaseService;
import io.gravitee.am.model.ActionLease;
import io.gravitee.am.repository.management.api.ActionLeaseRepository;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Maybe;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ActionLeaseServiceImpl implements ActionLeaseService {

    private final ActionLeaseRepository actionLeaseRepository;

    private final Node node;

    public ActionLeaseServiceImpl(@Lazy @Qualifier("managementActionLeaseRepository") ActionLeaseRepository actionLeaseRepository,
                                  @Lazy Node node) {
        this.actionLeaseRepository = actionLeaseRepository;
        this.node = node;
    }

    @Override
    public Maybe<ActionLease> acquireLease(String action, Duration duration) {
        return actionLeaseRepository.acquireLease(action, node.id(), duration);
    }
}

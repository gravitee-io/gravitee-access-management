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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationCursorRequest;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.cursor.CursorRequest;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.ApplicationCursorRepository;
import io.gravitee.am.repository.management.api.search.ApplicationCriteria;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.ApplicationSearcher;
import io.gravitee.am.service.model.ApplicationFilter;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@CustomLog
@Component
public class ApplicationSearcherImpl implements ApplicationSearcher {

    @Lazy
    @Autowired
    private ApplicationCursorRepository cursorRepository;

    @Autowired
    private ApplicationOwnerService applicationOwnerService;

    @Override
    public Single<CursorPage<Application, ApplicationCursorRequest>> searchByDomainCursor(String organization, String domain, ApplicationCursorRequest cursor, String ownerEmail, int limit) {
        log.debug("Search applications by domain {} with cursor pagination", domain);
        if(ownerEmail != null) {
            return collectIdsByOwnerEmail(ownerEmail, organization)
                    .flatMap(applicationIds -> cursorRepository.findByDomainAndIdsCursor(domain, applicationIds, cursor, limit));
        }
        return cursorRepository.findByDomainCursor(domain, cursor, limit);
    }


    @Override
    public Single<CursorPage<Application, ApplicationCursorRequest>> searchByDomainAndIdsCursor(String organization, String domain, List<String> applicationIds, ApplicationCursorRequest cursor, String ownerEmail, int limit) {
        log.debug("Find applications by domain {} and ids with cursor pagination", domain);
        if(ownerEmail != null) {
            return collectIdsByOwnerEmail(applicationIds, ownerEmail, organization)
                    .flatMap(ids -> cursorRepository.findByDomainAndIdsCursor(domain, ids, cursor, limit));
        }
        return cursorRepository.findByDomainAndIdsCursor(domain,applicationIds, cursor, limit);

    }

    private Single<List<String>> collectIdsByOwnerEmail(List<String> permissionScopedIds, String ownerEmail, String organizationId) {
        return applicationOwnerService.retrieveOwnerApplicationIds(ownerEmail, organizationId)
                .map(ids -> ids.stream().filter(permissionScopedIds::contains).collect(Collectors.toList()))
                .switchIfEmpty(Single.just(List.of()));
    }

    private Single<List<String>> collectIdsByOwnerEmail(String ownerEmail, String organizationId) {
        return applicationOwnerService.retrieveOwnerApplicationIds(ownerEmail, organizationId)
                .switchIfEmpty(Single.just(List.of()));
    }

}

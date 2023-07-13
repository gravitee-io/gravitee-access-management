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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.common.scim.parser.SCIMFilterParser;
import io.gravitee.am.management.service.CommonUserService;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.DomainService;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;

import javax.inject.Named;
import jakarta.ws.rs.BadRequestException;

/**
 * @author Titouan COMPIEGNE (titouuan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractUsersResource extends AbstractResource {

    protected static final int MAX_USERS_SIZE_PER_PAGE = 30;
    protected static final String MAX_USERS_SIZE_PER_PAGE_STRING = "30";

    @Autowired
    protected UserService userService;

    @Autowired
    @Named("managementOrganizationUserService")
    protected OrganizationUserService organizationUserService;

    @Autowired
    protected DomainService domainService;

    protected Single<Page<User>> searchUsers(ReferenceType referenceType,
                                             String referenceId,
                                             String query,
                                             String filter,
                                             int page,
                                             int size) {
        CommonUserService service = (referenceType == ReferenceType.ORGANIZATION ? organizationUserService : userService);
        return executeSearchUsers(service, referenceType, referenceId, query, filter, page, size);
    }

    private Single<Page<User>> executeSearchUsers(CommonUserService service, ReferenceType referenceType, String referenceId, String query, String filter, int page, int size) {
        if (query != null) {
            return service.search(referenceType, referenceId, query, page, Integer.min(size, MAX_USERS_SIZE_PER_PAGE));
        }
        if (filter != null) {
            return Single.defer(() -> {
                FilterCriteria filterCriteria = FilterCriteria.convert(SCIMFilterParser.parse(filter));
                return service.search(referenceType, referenceId, filterCriteria, page, Integer.min(size, MAX_USERS_SIZE_PER_PAGE));
            }).onErrorResumeNext(ex -> {
                if (ex instanceof IllegalArgumentException) {
                    return Single.error(new BadRequestException(ex.getMessage()));
                }
                return Single.error(ex);
            });
        }
        return service.findAll(referenceType, referenceId, page, Integer.min(size, MAX_USERS_SIZE_PER_PAGE));
    }

}

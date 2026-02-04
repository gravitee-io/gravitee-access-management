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
package io.gravitee.am.service;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.model.NewMembership;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface MembershipService {

    Maybe<Membership> findById(String id);

    Flowable<Membership> findByCriteria(ReferenceType referenceType, String referenceId, MembershipCriteria criteria);

    Flowable<Membership> findByCriteria(ReferenceType referenceType, MembershipCriteria criteria);

    Flowable<Membership> findByReference(String referenceId, ReferenceType referenceType);

    Flowable<Membership> findByMember(String memberId, MemberType memberType);

    Single<Membership> addOrUpdate(String organizationId, Membership membership, User principal);

    Single<Membership> setPlatformAdmin(String userId);

    Single<Map<String, Map<String, Object>>> getMetadata(List<Membership> memberships);

    Completable delete(Reference reference, String membershipId, User principal);

    default Single<Membership> addOrUpdate(String organizationId, Membership membership) {
        return addOrUpdate(organizationId, membership, null);
    }

    default Completable delete(Reference reference, String membershipId) {
        return delete(reference, membershipId, null);
    }

    /**
     * When adding membership to an application, some permissions are necessary on the application's domain.
     * These permissions are available through the DOMAIN_USER.
     * For convenience, to limit the number of actions an administrator must do to affect role on an application, the group or user will also inherit the DOMAIN_USER role on the application's domain.
     *
     * If the group or user already has a role on the domain, nothing is done.
     *
     * @see #addDomainUserRoleIfNecessary(String, String, String, NewMembership, User)
     */
    Completable addDomainUserRoleIfNecessary(String organizationId, String environmentId, String domainId, NewMembership newMembership, User principal);

    /**
     * When adding membership to a domain, some permissions are necessary on the domain's environment.
     * These permissions are available through the ENVIRONMENT_USER.
     * For convenience, to limit the number of actions an administrator must do to affect role on a domain, the group or user will also inherit the ENVIRONMENT_USER role on the domain's environment.
     *
     * If the group or user already has a role on the environment, nothing is done.
     */
    Completable addEnvironmentUserRoleIfNecessary(String organizationId, String environmentId, NewMembership newMembership, User principal);
}

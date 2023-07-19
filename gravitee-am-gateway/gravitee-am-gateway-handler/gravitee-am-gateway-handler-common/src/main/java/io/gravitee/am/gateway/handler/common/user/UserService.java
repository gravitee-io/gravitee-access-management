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
package io.gravitee.am.gateway.handler.common.user;

import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.repository.management.api.CommonUserRepository.UpdateActions;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserService {

    /**
     * Find a user by its technical id
     * @param id user technical id
     * @return end user
     */
    Maybe<User> findById(String id);

    /**
     * Find a user by its domain, its external id and its identity provider
     * @param domain user security domain
     * @param externalId user external id
     * @param source user identity provider
     * @return end user
     */
    Maybe<User> findByDomainAndExternalIdAndSource(String domain, String externalId, String source);

    /**
     * Find a user by its domain, its username and its identity provider
     * @param domain user security domain
     * @param username user username
     * @param source user identity provider
     * @return end user
     */
    Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source);

    /**
     * Find users by security domain and email
     * @param domain user security domain
     * @param criteria search criteria
     * @return
     */
    Single<List<User>> findByDomainAndCriteria(String domain, FilterCriteria criteria);

    /**
     * Create a new user
     * @param user user to create
     * @return created user
     */
    Single<User> create(User user);

    /**
     * Update an existing user
     * @param user user to update
     * @return updated user
     */
    default Single<User> update(User user) {
        return update(user, UpdateActions.updateAll());
    };

    Single<User> update(User user, UpdateActions updateActions);

    /**
     * Fetch additional data such as groups/roles to enhance user profile information
     * @param user end user
     * @return Enhanced user
     */
    Single<User> enhance(User user);

    /**
     * Add an MFA factor to an end-user
     * @param userId the end-user id
     * @param enrolledFactor the factor to enroll
     * @param principal the user who has performed this action
     * @return
     */
    Single<User> addFactor(String userId, EnrolledFactor enrolledFactor, io.gravitee.am.identityprovider.api.User principal);

    /**
     * Update an MFA factor to an end-user
     * @param userId the end-user id
     * @param enrolledFactor the factor to enroll
     * @param principal the user who has performed this action
     * @return
     */
    Single<User> updateFactor(String userId, EnrolledFactor enrolledFactor, io.gravitee.am.identityprovider.api.User principal);
}

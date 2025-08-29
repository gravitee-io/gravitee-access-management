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
package io.gravitee.am.dataplane.api.repository;

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.scim.Manager;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserRepository extends CrudRepository<User, String> {

    Maybe<User> findById(UserId id);

    Flowable<User> findAll(Reference reference);

    Single<Page<User>> findAll(Reference reference, int page, int size);

    /**
     * Same implementation as findAll for SCIM request as for SCIM pagination the startingIndex value is used instead of the page number.
     *
     * @param reference
     * @param startIndex index of the record from which the search as to start
     * @param count
     * @return
     */
    Single<Page<User>> findAllScim(Reference reference, int startIndex, int count);

    Single<Page<User>> search(Reference reference, String query, int page, int size);

    Single<Page<User>> search(Reference reference, FilterCriteria criteria, int page, int size);

    /**
     * Same implementation as search for SCIM request as for SCIM pagination the startingIndex value is used instead of the page number.
     *
     * @param reference
     * @param criteria
     * @param startIndex index of the record from which the search as to start
     * @param count
     * @return
     */
    Single<Page<User>> searchScim(Reference reference, FilterCriteria criteria, int startIndex, int count);

    Flowable<User> findByDomainAndEmail(String domain, String email, boolean strict);

    Maybe<User> findByUsernameAndDomain(String domain, String username);

    Maybe<User> findByUsernameAndSource(Reference reference, String username, String source);

    Maybe<User> findByUsernameAndSource(Reference reference, String username, String source, boolean includeLinkedIdentities);

    Maybe<User> findByExternalIdAndSource(Reference reference, String externalId, String source);

    Flowable<User> findByIdIn(Reference reference, List<String> ids);

    Maybe<User> findById(Reference reference, UserId userId);

    Single<Long> countByReference(Reference reference);

    Single<Long> countByApplication(String domain, String application);

    Single<Map<Object, Object>> statistics(AnalyticsQuery query);
    /**
     * Update the user profile information.
     * According to the actions provided, secondary information can be updated too (SCIM emails, SCIM PhoneNumbers, roles...)
     *
     * @param user
     * @param actions
     * @return
     */
    Single<User> update(User user, UpdateActions actions);

    Flowable<User> search(Reference reference, FilterCriteria criteria);

    Completable deleteByReference(Reference reference);

    /**
     * Used to specify if some user information need to be updated
     * in addition of the main User Profile information.
     */
    class UpdateActions {
        private boolean role = true;
        private boolean dynamicRole = true;
        private boolean dynamicGroup = true;
        private boolean attributes = true;
        private boolean entitlements = true;
        private boolean addresses = true;
        private boolean identities = true;
        private boolean manager = true;

        UpdateActions() {}

        public boolean updateRole() {
            return role;
        }

        public UpdateActions updateRole(boolean role) {
            this.role = role;
            return this;
        }

        public boolean updateDynamicRole() {
            return dynamicRole;
        }

        public UpdateActions updateDynamicRole(boolean dynamicRole) {
            this.dynamicRole = dynamicRole;
            return this;
        }

        public boolean updateDynamicGroup() {
            return dynamicGroup;
        }

        public UpdateActions updateDynamicGroup(boolean dynamicGroup) {
            this.dynamicGroup = dynamicGroup;
            return this;
        }

        public boolean updateAttributes() {
            return attributes;
        }

        public UpdateActions updateAttributes(boolean attributes) {
            this.attributes = attributes;
            return this;
        }

        public boolean updateEntitlements() {
            return entitlements;
        }

        public UpdateActions updateEntitlements(boolean entitlements) {
            this.entitlements = entitlements;
            return this;
        }

        public boolean updateAddresses() {
            return addresses;
        }

        public UpdateActions updateAddresses(boolean addresses) {
            this.addresses = addresses;
            return this;
        }
        public boolean updateIdentities() {
            return identities;
        }

        public UpdateActions updateIdentities(boolean identities) {
            this.identities = identities;
            return this;
        }

        public boolean updateManager() {
            return manager;
        }

        public UpdateActions updateManager(boolean manager) {
            this.manager = manager;
            return this;
        }

        public boolean updateRequire() {
            return (addresses || attributes || entitlements || role || dynamicRole || dynamicGroup || identities || manager);
        }

        public static UpdateActions updateAll() {
            return new UserRepository.UpdateActions();
        }

        public static UpdateActions none() {
            return new UserRepository.UpdateActions()
                    .updateRole(false)
                    .updateDynamicRole(false)
                    .updateDynamicGroup(false)
                    .updateEntitlements(false)
                    .updateAttributes(false)
                    .updateAddresses(false)
                    .updateIdentities(false)
                    .updateManager(false);
        }

        public static UpdateActions build(io.gravitee.am.model.User existingUser, io.gravitee.am.model.User updatedUser) {
            UpdateActions actions = new UserRepository.UpdateActions();
            actions.updateEntitlements(needUpdate(existingUser.getEntitlements(), updatedUser.getEntitlements()));

            actions.updateAttributes((needUpdate(existingUser.getEmails(), updatedUser.getEmails()) ||
                    needUpdate(existingUser.getPhoneNumbers(), updatedUser.getPhoneNumbers()) ||
                    needUpdate(existingUser.getIms(), updatedUser.getIms()) ||
                    needUpdate(existingUser.getPhotos(), updatedUser.getPhotos())));

            actions.updateAddresses(needUpdate(existingUser.getAddresses(), updatedUser.getAddresses()));
            actions.updateRole(needUpdate(existingUser.getRoles(), updatedUser.getRoles()));
            actions.updateDynamicRole(needUpdate(existingUser.getDynamicRoles(), updatedUser.getDynamicRoles()));
            actions.updateIdentities(needUpdate(existingUser.getIdentities(), updatedUser.getIdentities()));
            actions.updateManager(
                    needUpdate(
                            existingUser.getManager() != null ? List.of(existingUser.getManager()) : null,
                            updatedUser.getManager() != null ? List.of(updatedUser.getManager()) : null));

            return actions;
        }

        private static boolean needUpdate(List existing, List update) {
            return !(((existing == null || existing.isEmpty()) && (update == null || update.isEmpty()))
                    || Objects.equals(existing, update));
        }
    }
}

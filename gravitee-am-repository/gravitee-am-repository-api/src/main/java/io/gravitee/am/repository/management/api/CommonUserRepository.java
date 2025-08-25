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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;
import java.util.Objects;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CommonUserRepository extends CrudRepository<User, String> {

    /**
     * Update the user profile information.
     * According to the actions provided, secondary information can be updated too (SCIM emails, SCIM PhoneNumbers, roles...)
     *
     * @param user
     * @param actions
     * @return
     */
    Single<User> update(User user, UpdateActions actions);

    Flowable<User> findAll(ReferenceType referenceType, String referenceId);

    Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size);

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size);

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria criteria, int page, int size);

    Flowable<User> search(ReferenceType referenceType, String referenceId, FilterCriteria criteria);

    Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source);

    Maybe<User> findByExternalIdAndSource(ReferenceType referenceType, String referenceId, String externalId, String source);

    Flowable<User> findByIdIn(List<String> ids);

    Maybe<User> findById(Reference reference, UserId userId);

    Completable deleteByReference(ReferenceType referenceType, String referenceId);

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
            return new UpdateActions();
        }

        public static UpdateActions none() {
            return new UpdateActions()
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
            UpdateActions actions = new UpdateActions();
            actions.updateEntitlements(needUpdate(existingUser.getEntitlements(), updatedUser.getEntitlements()));

            actions.updateAttributes((needUpdate(existingUser.getEmails(), updatedUser.getEmails()) ||
                    needUpdate(existingUser.getPhoneNumbers(), updatedUser.getPhoneNumbers()) ||
                    needUpdate(existingUser.getIms(), updatedUser.getIms()) ||
                    needUpdate(existingUser.getPhotos(), updatedUser.getPhotos())));

            actions.updateAddresses(needUpdate(existingUser.getAddresses(), updatedUser.getAddresses()));
            actions.updateRole(needUpdate(existingUser.getRoles(), updatedUser.getRoles()));
            actions.updateDynamicRole(needUpdate(existingUser.getDynamicRoles(), updatedUser.getDynamicRoles()));
            actions.updateDynamicGroup(needUpdate(existingUser.getDynamicGroups(), updatedUser.getDynamicGroups()));
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

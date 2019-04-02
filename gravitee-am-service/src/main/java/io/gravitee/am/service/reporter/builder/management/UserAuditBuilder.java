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
package io.gravitee.am.service.reporter.builder.management;

import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.model.User;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuditBuilder extends ManagementAuditBuilder<UserAuditBuilder> {

    public UserAuditBuilder() {
        super();
    }

    public UserAuditBuilder user(User user) {
        if (EventType.USER_CREATED.equals(getType()) || EventType.USER_UPDATED.equals(getType())) {
            setNewValue(user);
        }
        domain(user.getDomain());
        setTarget(user.getId(), EntityType.USER, user.getUsername(), getDisplayName(user), user.getDomain());
        return this;
    }

    private String getDisplayName(User user) {
        return user.getDisplayName() != null ? user.getDisplayName() :
                user.getFirstName() != null ? user.getFirstName() + (user.getLastName() != null ? " " + user.getLastName() : "") :
                        user.getUsername();
    }
}

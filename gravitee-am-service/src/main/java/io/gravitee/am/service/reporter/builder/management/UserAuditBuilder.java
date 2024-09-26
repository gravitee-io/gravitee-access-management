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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.Device;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.User;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.vertx.core.json.Json;

import java.util.Map;
import java.util.Set;

import static io.gravitee.am.common.audit.EventType.REGISTRATION_VERIFY_ACCOUNT;
import static io.gravitee.am.common.audit.EventType.USER_CREATED;
import static io.gravitee.am.common.audit.EventType.USER_ROLES_ASSIGNED;
import static io.gravitee.am.common.audit.EventType.USER_UPDATED;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuditBuilder extends ManagementAuditBuilder<UserAuditBuilder> {

    private final Set<String> SENSITIVE_DATA_USER_EVENTS = Set.of(
            USER_CREATED,
            USER_UPDATED,
            USER_ROLES_ASSIGNED,
            REGISTRATION_VERIFY_ACCOUNT
    );

    private Map<String, String> accountToken;


    public UserAuditBuilder() {
        super();
    }

    public UserAuditBuilder user(User user) {
        if (user != null) {
            if (isSensitiveEventType()) {
                setNewValue(user);
            }
            reference(new Reference(user.getReferenceType(), user.getReferenceId()));
            setTarget(user.getId(), EntityType.USER, user.getUsername(), getDisplayName(user), user.getReferenceType(), user.getReferenceId(), user.getExternalId(), user.getSource());
        }
        return this;
    }

    public UserAuditBuilder deletedDevice(Device device) {
        if(device != null){
            setTarget(device.getDeviceId(), EntityType.DEVICE_IDENTIFIER, null, device.getDeviceIdentifierId(), device.getReferenceType(), device.getReferenceId());
        }
        return this;
    }

    public UserAuditBuilder accountToken(AccountAccessToken token) {
        if (token != null) {
            accountToken = Map.of("id", token.tokenId(), "name", token.name());
        }
        return this;
    }

    private boolean isSensitiveEventType() {
        return ofNullable(getType()).filter(SENSITIVE_DATA_USER_EVENTS::contains).isPresent();
    }

    private String getDisplayName(User user) {
        return user.getDisplayName() != null ? user.getDisplayName() :
                user.getFirstName() != null ? user.getFirstName() + (user.getLastName() != null ? " " + user.getLastName() : "") :
                        user.getUsername();
    }

    @Override
    protected Object removeSensitiveData(Object value) {
        if (value instanceof User user) {
            User safeUser = new User(user);
            safeUser.setPassword(null);
            safeUser.setRegistrationAccessToken(null);
            if (safeUser.getAdditionalInformation() != null) {
                safeUser.getAdditionalInformation().remove(ConstantKeys.OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY);
                safeUser.getAdditionalInformation().remove(ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY);
            }
            return safeUser;
        }
        return value;
    }

    @Override
    public Audit build(ObjectMapper mapper) {
        var audit = super.build(mapper);
        if (accountToken != null) {
            audit.getOutcome().setMessage(Json.encode(Map.of("token", accountToken)));
        }
        return audit;
    }
}

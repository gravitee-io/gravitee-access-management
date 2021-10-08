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
import io.gravitee.am.model.DeviceIdentifier;

import java.util.List;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceIdentifierAuditBuilder extends ManagementAuditBuilder<DeviceIdentifierAuditBuilder> {

    private static final List<String> NEW_VALUE_EVENT_TYPE = List.of(
            EventType.DEVICE_IDENTIFIER_CREATED,
            EventType.BOT_DETECTION_UPDATED
    );

    public DeviceIdentifierAuditBuilder() {
        super();
    }

    public DeviceIdentifierAuditBuilder deviceIdentifier(DeviceIdentifier deviceIdentifier) {
        if (NEW_VALUE_EVENT_TYPE.contains(getType())) {
            setNewValue(deviceIdentifier);
        }

        referenceId(deviceIdentifier.getReferenceId());
        referenceType(deviceIdentifier.getReferenceType());

        setTarget(deviceIdentifier.getId(), EntityType.DEVICE_IDENTIFIER, null, deviceIdentifier.getName(), deviceIdentifier.getReferenceType(), deviceIdentifier.getReferenceId());
        return this;
    }
}

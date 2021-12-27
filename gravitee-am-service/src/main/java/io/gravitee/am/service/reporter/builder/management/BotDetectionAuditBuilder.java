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
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.ReferenceType;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BotDetectionAuditBuilder extends ManagementAuditBuilder<BotDetectionAuditBuilder> {

    public BotDetectionAuditBuilder() {
        super();
    }

    public BotDetectionAuditBuilder botDetection(BotDetection botDetection) {
        if (botDetection != null) {
            if (EventType.BOT_DETECTION_CREATED.equals(getType()) || EventType.BOT_DETECTION_UPDATED.equals(getType())) {
                setNewValue(botDetection);
            }

            referenceId(botDetection.getReferenceId());
            referenceType(botDetection.getReferenceType());

            setTarget(botDetection.getId(), EntityType.BOT_DETECTION, null, botDetection.getName(), botDetection.getReferenceType(), botDetection.getReferenceId());
        }
        return this;
    }
}

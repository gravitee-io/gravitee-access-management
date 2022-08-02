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
import io.gravitee.am.model.Theme;
import io.gravitee.am.service.reporter.builder.AuditBuilder;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThemeAuditBuilder extends ManagementAuditBuilder<ThemeAuditBuilder> {

    public ThemeAuditBuilder() {
        super();
    }

    public ThemeAuditBuilder theme(Theme theme) {
        if (theme != null) {
            if (EventType.THEME_DELETED.equals(getType())
                    || EventType.THEME_CREATED.equals(getType())
                    || EventType.THEME_UPDATED.equals(getType())) {
                setNewValue(theme);
            }

            referenceType(theme.getReferenceType());
            referenceId(theme.getReferenceId());

            setTarget(theme.getId(), EntityType.THEME, null, null, theme.getReferenceType(), theme.getReferenceId());
        }
        return this;
    }
    
}

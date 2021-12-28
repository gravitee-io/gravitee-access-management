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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ReporterAuditBuilder extends ManagementAuditBuilder<ReporterAuditBuilder> {

    public ReporterAuditBuilder() {
        super();
    }

    public ReporterAuditBuilder reporter(Reporter reporter) {
        if (reporter != null) {
            if (EventType.REPORTER_CREATED.equals(getType()) || EventType.REPORTER_UPDATED.equals(getType())) {
                setNewValue(reporter);
            }
            domain(reporter.getDomain());
            setTarget(reporter.getId(), EntityType.REPORTER, null, reporter.getName(), ReferenceType.DOMAIN, reporter.getDomain());
        }
        return this;
    }
}

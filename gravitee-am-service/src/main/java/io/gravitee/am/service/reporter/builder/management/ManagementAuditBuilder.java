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

import io.gravitee.am.model.Platform;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.reporter.builder.AuditBuilder;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class ManagementAuditBuilder<T extends ManagementAuditBuilder<T>> extends AuditBuilder<T> {

    /**
     * Management events are triggered by the admin client (the portal/API)
     */
    private static final String ADMIN_CLIENT = "admin";
    private static final String SYSTEM = "system";

    protected ManagementAuditBuilder() {
        super();
        client(ADMIN_CLIENT);
        setActor(SYSTEM, SYSTEM, SYSTEM, SYSTEM, ReferenceType.PLATFORM, Platform.DEFAULT);
    }

    public T systemPrincipal() {
        setActor(SYSTEM, SYSTEM, SYSTEM, SYSTEM, ReferenceType.PLATFORM, Platform.DEFAULT);
        return (T) this;
    }

}

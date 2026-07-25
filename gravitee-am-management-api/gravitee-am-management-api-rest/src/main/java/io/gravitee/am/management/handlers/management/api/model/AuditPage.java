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
package io.gravitee.am.management.handlers.management.api.model;

import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.model.Audit;

import java.util.Collection;

/**
 * The shape every audit listing returns, declared once so the generated clients type it as a page
 * rather than the bare array the annotations used to claim. Shared by the domain, organization and
 * user audit endpoints, because Swagger keys schemas on the simple class name.
 *
 * @author GraviteeSource Team
 */
public final class AuditPage extends Page<Audit> {

    public AuditPage(Collection<Audit> data, int currentPage, long totalCount) {
        super(data, currentPage, totalCount);
    }
}

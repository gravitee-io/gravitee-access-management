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
package io.gravitee.am.service.model;

import java.util.List;

/**
 * Raw filter parameters for application list/search endpoints.
 * Resolution of ownerEmail into application IDs is performed by the service layer.
 * <p>
 * {@code permissionScopedIds} — when non-null, the result is restricted to this set of
 * application IDs (derived from the caller's READ permissions). The service intersects
 * this with any owner-resolved IDs so the final query always respects both constraints.
 *
 * @author GraviteeSource Team
 */
public record ApplicationFilter(String status, String ownerEmail, List<String> permissionScopedIds) {

    public static final String STATUS_ENABLED = "enabled";
    public static final String STATUS_DISABLED = "disabled";

    public ApplicationFilter(String status, String ownerEmail) {
        this(status, ownerEmail, null);
    }

    public boolean hasStatusFilter() {
        return status != null && !status.isBlank();
    }

    public boolean hasOwnerEmailFilter() {
        return ownerEmail != null && !ownerEmail.isBlank();
    }

    public boolean hasPermissionScopedIds() {
        return permissionScopedIds != null;
    }

    public static ApplicationFilter empty() {
        return new ApplicationFilter(null, null, null);
    }
}

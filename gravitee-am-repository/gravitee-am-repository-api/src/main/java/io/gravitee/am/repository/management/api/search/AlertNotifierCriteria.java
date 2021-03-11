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
package io.gravitee.am.repository.management.api.search;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AlertNotifierCriteria {

    private boolean logicalOR;
    private Boolean enabled;
    private List<String> ids;

    public Optional<Boolean> isEnabled() {
        return Optional.ofNullable(enabled);
    }

    public Optional<List<String>> getIds() {
        return Optional.ofNullable(ids);
    }

    public boolean isLogicalOR() {
        return logicalOR;
    }

    public void setLogicalOR(boolean logicalOR) {
        this.logicalOR = logicalOR;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlertNotifierCriteria that = (AlertNotifierCriteria) o;
        return logicalOR == that.logicalOR && Objects.equals(enabled, that.enabled) && Objects.equals(ids, that.ids);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logicalOR, enabled, ids);
    }
}

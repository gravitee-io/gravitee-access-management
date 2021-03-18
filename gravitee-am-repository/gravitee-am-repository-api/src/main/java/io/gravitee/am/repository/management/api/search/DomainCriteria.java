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
public class DomainCriteria {

    private boolean logicalOR;

    private Boolean alertEnabled;

    public Optional<Boolean> isAlertEnabled() {

        return Optional.ofNullable(alertEnabled);
    }

    public void setAlertEnabled(boolean alertEnabled) {
        this.alertEnabled = alertEnabled;
    }

    public boolean isLogicalOR() {
        return logicalOR;
    }

    public void setLogicalOR(boolean logicalOR) {
        this.logicalOR = logicalOR;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DomainCriteria that = (DomainCriteria) o;
        return logicalOR == that.logicalOR && Objects.equals(alertEnabled, that.alertEnabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logicalOR, alertEnabled);
    }
}

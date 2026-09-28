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

package io.gravitee.am.model.idp;

import java.util.Comparator;
import java.util.Objects;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationIdentityProvider implements Comparable<ApplicationIdentityProvider> {

    private static final Comparator<ApplicationIdentityProvider> ORDER = Comparator
            .comparing((ApplicationIdentityProvider appIdp) -> appIdp.getPriority() < 0)
            .thenComparingInt(ApplicationIdentityProvider::getPriority)
            .thenComparing(ApplicationIdentityProvider::getIdentity, nullsFirst(naturalOrder()))
            .thenComparing(ApplicationIdentityProvider::getSelectionRule, nullsFirst(naturalOrder()));

    private String identity;
    private String selectionRule;
    private int priority;

    public String getIdentity() {
        return identity;
    }

    public String getSelectionRule() {
        return selectionRule;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public void setSelectionRule(String selectionRule) {
        this.selectionRule = selectionRule;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationIdentityProvider that = (ApplicationIdentityProvider) o;
        return priority == that.priority && Objects.equals(identity, that.identity) && Objects.equals(selectionRule, that.selectionRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity, selectionRule, priority);
    }

    @Override
    public int compareTo(ApplicationIdentityProvider o) {
        return ORDER.compare(this, o);
    }
}

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
package io.gravitee.am.policy.enrich.profile.configuration;

import java.util.Objects;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Property {

    private String claim;

    private String claimValue;

    public Property() {}

    public Property(String claim, String value) {
        this.claim = claim;
        this.claimValue = value;
    }

    public String getClaimValue() {
        return claimValue;
    }

    public void setClaimValue(String claimValue) {
        this.claimValue = claimValue;
    }

    public String getClaim() {
        return claim;
    }

    public void setClaim(String claim) {
        this.claim = claim;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Property that = (Property) o;
        return Objects.equals(claimValue, that.claimValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(claimValue);
    }
}

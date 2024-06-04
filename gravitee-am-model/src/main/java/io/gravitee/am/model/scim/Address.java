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
package io.gravitee.am.model.scim;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * !!! WARNING
 * !!! remember to update hasCode & equals if new field is added
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Setter
@Getter
public class Address {

    private String type;
    private String formatted;
    private String streetAddress;
    private String locality;
    private String region;
    private String postalCode;
    private String country;
    private Boolean primary;

    public Boolean isPrimary() {
        return primary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Address)) return false;

        Address address = (Address) o;

        if (!Objects.equals(type, address.type)) return false;
        if (!Objects.equals(formatted, address.formatted)) return false;
        if (!Objects.equals(streetAddress, address.streetAddress))
            return false;
        if (!Objects.equals(locality, address.locality)) return false;
        if (!Objects.equals(region, address.region)) return false;
        if (!Objects.equals(postalCode, address.postalCode)) return false;
        if (!Objects.equals(country, address.country)) return false;
        return Objects.equals(primary, address.primary);
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (formatted != null ? formatted.hashCode() : 0);
        result = 31 * result + (streetAddress != null ? streetAddress.hashCode() : 0);
        result = 31 * result + (locality != null ? locality.hashCode() : 0);
        result = 31 * result + (region != null ? region.hashCode() : 0);
        result = 31 * result + (postalCode != null ? postalCode.hashCode() : 0);
        result = 31 * result + (country != null ? country.hashCode() : 0);
        result = 31 * result + (primary != null ? primary.hashCode() : 0);
        return result;
    }
}

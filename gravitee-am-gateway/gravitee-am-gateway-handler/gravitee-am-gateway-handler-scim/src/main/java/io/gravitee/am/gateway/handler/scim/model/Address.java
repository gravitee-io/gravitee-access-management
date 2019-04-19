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
package io.gravitee.am.gateway.handler.scim.model;

/**
 * A physical mailing address for this user.
 *
 * See <a href="https://tools.ietf.org/html/rfc7643#section-4.1.2">4.1.2. Multi-Valued Attributes</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Address {

    /**
     * A label indicating the attribute's function, e.g., 'work' or 'home'.
     */
    private String type;

    /**
     * The full mailing address, formatted for display or use
     *  with a mailing label.  This attribute MAY contain newlines.
     */
    private String formatted;

    /**
     * The full street address component, which may
     * include house number, street name, P.O. box, and multi-line
     *  extended street address information.  This attribute MAY contain newlines.
     */
    private String streetAddress;

    /**
     * The city or locality component.
     */
    private String locality;

    /**
     * The state or region component.
     */
    private String region;

    /**
     * The zip code or postal code component.
     */
    private String postalCode;

    /**
     * The country name component.
     */
    private String country;

    private Boolean primary;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFormatted() {
        return formatted;
    }

    public void setFormatted(String formatted) {
        this.formatted = formatted;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Boolean isPrimary() {
        return primary;
    }

    public void setPrimary(Boolean primary) {
        this.primary = primary;
    }
}

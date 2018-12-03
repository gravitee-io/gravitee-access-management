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
}

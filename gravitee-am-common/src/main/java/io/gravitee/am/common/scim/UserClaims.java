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
package io.gravitee.am.common.scim;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserClaims {

    /**
     * Used to identify the relationship between the organization and the user.
     * Typical values used might be "Contractor", "Employee", "Intern", "Temp", "External", and "Unknown", but any value may be used.
     */
    String USER_TYPE = "scim_user_type";

    /**
     * Indicates the user's preferred written or spoken languages and is generally used for selecting a localized user interface.
     * The value indicates the set of natural languages that are preferred.
     * The format of the value is the same as the HTTP Accept-Language header field (not including "Accept-Language:") and is specifie in Section 5.3.5 of [RFC7231].
     * The intent of this value is to enable cloud applications to perform matching of language tags [RFC4647] to the user's language preferences, regardless of what
     *  may be indicated by a user agent (which might be shared), or in an interaction that does not involve a user (such as in a delegated OAuth 2.0 [RFC6749] style interaction) where normal HTTP Accept-Language header negotiation cannot take place.
     */
    String PREFERRED_LANGUAGE = "scim_preferred_language";

    /**
     * Email addresses for the user.
     */
    String EMAILS = "scim_emails";

    /**
     * Phone numbers for the user.
     */
    String PHONE_NUMBERS = "scim_phone_numbers";

    /**
     * Instant messaging address for the user.
     */
    String IMS = "scim_ims";

    /**
     * A URI that is a uniform resource locator (as defined in Section 1.1.3 of [RFC3986]) that points to a resource location representing the user's image.
     */
    String PHOTOS = "scim_photos";

    /**
     * A list of entitlements for the user that represent a thing the user has.
     */
    String ENTITLEMENTS = "scim_entitlements";

    /**
     * Physical mailing addresses for the user.
     */
    String ADDRESSES = "scim_addresses";

    /**
     * Physical mailing addresses for the user.
     */
    String ROLES = "scim_roles";

    /**
     * A list of certificates associated with the resource (e.g., a User).
     */
    String CERTIFICATES = "scim_certificates";
}

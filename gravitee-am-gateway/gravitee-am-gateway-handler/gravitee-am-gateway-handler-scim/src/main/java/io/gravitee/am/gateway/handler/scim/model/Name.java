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
 * The components of the user's name.
 *
 * See <a href="https://tools.ietf.org/html/rfc7643#section-4.1.1">4.1.1. Singular Attributes</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Name {

    /**
     *  The full name, including all middle names, titles, and suffixes as appropriate, formatted for display (e.g., "Ms. Barbara Jane Jensen, III").
     */
    private String formatted;

    /**
     * The family name of the User, or last name in most Western languages (e.g., "Jensen" given the full name "Ms. Barbara Jane Jensen, III").
     */
    private String familyName;

    /**
     * The given name of the User, or first name in most Western languages (e.g., "Barbara" given the full name" Ms. Barbara Jane Jensen, III").
     */
    private String givenName;

    /**
     *  The middle name(s) of the User (e.g., "Jane" given the full name "Ms. Barbara Jane Jensen, III").
     */
    private String middleName;

    /**
     * The honorific prefix(es) of the User, or title in most Western languages (e.g., "Ms." given the full name "Ms. Barbara Jane Jensen, III").
     */
    private String honorificPrefix;

    /**
     * The honorific suffix(es) of the User, or suffix in most Western languages (e.g., "III" given the full name "Ms. Barbara Jane Jensen, III").
     */
    private String honorificSuffix;

    public String getFormatted() {
        return formatted;
    }

    public void setFormatted(String formatted) {
        this.formatted = formatted;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getHonorificPrefix() {
        return honorificPrefix;
    }

    public void setHonorificPrefix(String honorificPrefix) {
        this.honorificPrefix = honorificPrefix;
    }

    public String getHonorificSuffix() {
        return honorificSuffix;
    }

    public void setHonorificSuffix(String honorificSuffix) {
        this.honorificSuffix = honorificSuffix;
    }
}

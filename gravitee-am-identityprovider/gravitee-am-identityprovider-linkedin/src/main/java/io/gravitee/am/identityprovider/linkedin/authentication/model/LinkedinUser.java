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
package io.gravitee.am.identityprovider.linkedin.authentication.model;

/**
 * GitHub User claims
 *
 * See <a href="https://developer.github.com/v3/users/#get-a-single-user>Get a single user</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface LinkedinUser {
    String ID = "id";
    String FIRSTNAME = "localizedFirstName";
    String LASTNAME = "localizedLastName";
    String MAIDENNAME = "localizedMaidenName";
    String HEADLINE = "localizedHeadline";
    String PROFILE_URL = "vanityName";

}

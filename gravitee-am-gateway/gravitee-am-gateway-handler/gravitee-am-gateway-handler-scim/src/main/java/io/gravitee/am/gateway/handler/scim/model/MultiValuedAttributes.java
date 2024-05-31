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

import java.util.List;

import static org.springframework.util.StringUtils.hasLength;

/**
 * Helper to identify User & Group attributes which should be managed as list.
 * This has been introduced to manage https://github.com/gravitee-io/issues/issues/9674
 */
class MultiValuedAttributes {
    private static final List<String> listOfSingularValue = List.of("roles", "entitlements");
    private static final List<String> listOfMultiAttributeValue = List.of("emails", "phoneNumbers", "ims", "photos", "addresses", "groups", "x509Certificates", "members");

    private MultiValuedAttributes() {}

    static boolean isListOfObjects(String path) {
        return hasLength(path) && listOfMultiAttributeValue.contains(path);
    }

    static boolean isListOfSingular(String path) {
        return hasLength(path) && listOfSingularValue.contains(path);
    }
}

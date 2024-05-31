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
package io.gravitee.am.gateway.handler.oidc.service.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

import static io.gravitee.am.common.oidc.SubjectType.PUBLIC;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SubjectTypeUtils {

    private static final List<String> SUPPORTED_SUBJECT_TYPES = List.of(PUBLIC);

    /**
     * Return the supported list of subject types.
     *
     * @return an unmodifiable list of supported subject types.
     */
    public static List<String> getSupportedSubjectTypes() {
        return List.copyOf(SUPPORTED_SUBJECT_TYPES);
    }

    /**
     * Checks if the given subject type is valid.
     *
     * @param subjectType the subject type to validate.
     * @return true if the subject type is supported, false otherwise.
     */
    public static boolean isValidSubjectType(String subjectType) {
        return SUPPORTED_SUBJECT_TYPES.contains(subjectType);
    }
}

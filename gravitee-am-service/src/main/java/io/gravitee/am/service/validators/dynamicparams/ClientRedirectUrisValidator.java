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
package io.gravitee.am.service.validators.dynamicparams;

import io.gravitee.am.service.utils.EvaluableRedirectUri;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClientRedirectUrisValidator {

    public boolean validateRedirectUris(List<String> redirectUris) {
        Map<String, Long> counts = redirectUris.stream()
                .map(EvaluableRedirectUri::new)
                .collect(Collectors.groupingBy(EvaluableRedirectUri::getRootUrl, Collectors.counting()));
        return counts.values().stream().allMatch(size -> size <= 1);
    }
}

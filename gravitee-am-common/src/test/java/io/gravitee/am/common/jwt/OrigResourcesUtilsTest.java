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
package io.gravitee.am.common.jwt;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class OrigResourcesUtilsTest {

    @Test
    public void shouldReturnEmptySet_whenJwtIsNull() {
        Set<String> result = OrigResourcesUtils.extractOrigResources(null);
        assertThat(result).isEmpty();
    }

    @Test
    public void shouldReturnEmptySet_whenClaimMissing() {
        Map<String, Object> jwt = new HashMap<>();
        Set<String> result = OrigResourcesUtils.extractOrigResources(jwt);
        assertThat(result).isEmpty();
    }

    @Test
    public void shouldReturnEmptySet_whenClaimNull() {
        Map<String, Object> jwt = new HashMap<>();
        jwt.put(Claims.ORIG_RESOURCES, null);
        Set<String> result = OrigResourcesUtils.extractOrigResources(jwt);
        assertThat(result).isEmpty();
    }

    @Test
    public void shouldExtractSingleString() {
        Map<String, Object> jwt = new HashMap<>();
        jwt.put(Claims.ORIG_RESOURCES, "https://api.example.com/photos");
        Set<String> result = OrigResourcesUtils.extractOrigResources(jwt);
        assertThat(result).containsExactly("https://api.example.com/photos");
    }

    @Test
    public void shouldExtractListOfStrings_ignoreNonStrings() {
        Map<String, Object> jwt = new HashMap<>();
        jwt.put(Claims.ORIG_RESOURCES, List.of(
                "https://api.example.com/photos",
                123, // ignored
                "https://api.example.com/albums",
                true // ignored
        ));
        Set<String> result = OrigResourcesUtils.extractOrigResources(jwt);
        assertThat(result)
                .containsExactlyInAnyOrder(
                        "https://api.example.com/photos",
                        "https://api.example.com/albums");
    }

    @Test
    public void shouldDeduplicateListEntries() {
        Map<String, Object> jwt = new HashMap<>();
        jwt.put(Claims.ORIG_RESOURCES, List.of(
                "https://api.example.com/photos",
                "https://api.example.com/photos"));
        Set<String> result = OrigResourcesUtils.extractOrigResources(jwt);
        assertThat(result)
                .hasSize(1)
                .contains("https://api.example.com/photos");
    }
}



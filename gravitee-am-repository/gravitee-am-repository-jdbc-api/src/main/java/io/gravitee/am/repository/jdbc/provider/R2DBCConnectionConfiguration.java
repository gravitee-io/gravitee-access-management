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
package io.gravitee.am.repository.jdbc.provider;

import io.gravitee.am.repository.provider.ConnectionConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface R2DBCConnectionConfiguration extends ConnectionConfiguration {


    String getProtocol();

    String getDatabase();

    String getUser();

    String getPassword();

    List<Map<String, String>> getOptions();

    default Stream<Map.Entry<String, String>> optionsStream() {
        List<Map<String, String>> options = getOptions();
        if (options == null) {
            return Stream.empty();
        } else {
            return options.stream()
                    .map(map -> Map.entry(map.get("option"), map.get("value")));
        }

    }

    default Optional<String> getOption(String key) {
        return optionsStream()
                .filter(entry -> entry.getKey().equals(key))
                .map(Map.Entry::getValue)
                .findFirst();
    }

}

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
package io.gravitee.am.service.reporter.attribute;

import io.gravitee.am.model.User;
import lombok.CustomLog;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Attribute names a reporter attribute mapping never exports.
 *
 * @author GraviteeSource Team
 */
@Component
@CustomLog
public class SensitiveAttributeDenylist implements InitializingBean {

    static final String DENIED_ATTRIBUTE_KEY = "reporters.audits.attribute_mappings.denied_attributes[%d]";

    static final Set<String> BASELINE = Stream.concat(
            User.SENSITIVE_ADDITIONAL_PROPERTIES.stream(),
            Stream.of(
                    "access_token", "refresh_token", "id_token", "token",
                    "registration_access_token", "authorization",
                    "password", "old_password", "new_password", "confirm_password",
                    "client_secret", "secret", "shared_secret",
                    "private_key", "api_key",
                    "credential", "credentials",
                    "assertion", "client_assertion", "passwordless_assertion",
                    "otp", "otp_code", "mfa_code", "recovery_code", "verification_code",
                    "challenge", "code_verifier")).collect(Collectors.toUnmodifiableSet());

    private static final int MAX_DEPTH = 10;

    static final Set<String> DENIED_SUFFIXES = Set.of(
            "password", "passwd", "secret", "token", "credential", "credentials", "privatekey", "apikey");

    @Autowired
    private Environment environment;

    private Set<String> denied = normalize(BASELINE);

    /**
     * Strips separators and case so that {@code client_secret}, {@code clientSecret} and {@code CLIENT-SECRET}
     * are one name.
     */
    static String normalize(String name) {
        return name == null ? "" : name.toLowerCase().replace("_", "").replace("-", "").replace(".", "");
    }

    private static Set<String> normalize(Set<String> names) {
        return names.stream().map(SensitiveAttributeDenylist::normalize).collect(Collectors.toUnmodifiableSet());
    }

    public boolean isDenied(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return false;
        }
        return denied.contains(normalized)
                || DENIED_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }

    /**
     * @return {@code attributes} without the denied names, at any depth
     */
    public Map<String, Object> scrubbedCopyOf(Map<String, ?> attributes) {
        return attributes == null ? null : scrubbedMap(attributes, MAX_DEPTH);
    }

    private Map<String, Object> scrubbedMap(Map<?, ?> attributes, int remainingDepth) {
        Map<String, Object> scrubbed = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            String name = key == null ? null : key.toString();
            if (!isDenied(name)) {
                scrubbedValue(value, remainingDepth).ifPresent(kept -> scrubbed.put(name, kept));
            }
        });
        return scrubbed;
    }

    private Optional<Object> scrubbedValue(Object value, int remainingDepth) {
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            if (remainingDepth == 0) {
                return Optional.empty();
            }
            if (value instanceof Map<?, ?> nested) {
                return Optional.of(scrubbedMap(nested, remainingDepth - 1));
            }
            return Optional.of(((Collection<?>) value).stream()
                    .flatMap(element -> scrubbedValue(element, remainingDepth - 1).stream())
                    .toList());
        }
        return Optional.ofNullable(value);
    }

    @Override
    public void afterPropertiesSet() {
        Set<String> configured = new HashSet<>(normalize(BASELINE));
        int i = 0;
        do {
            final var name = environment.getProperty(DENIED_ATTRIBUTE_KEY.formatted(i), String.class);
            if (name != null && !name.isBlank()) {
                log.debug("Attribute '{}' will never be exported by a reporter attribute mapping", name);
                configured.add(normalize(name));
            }
        }
        while (environment.containsProperty(DENIED_ATTRIBUTE_KEY.formatted(++i)));
        this.denied = Set.copyOf(configured);
    }
}

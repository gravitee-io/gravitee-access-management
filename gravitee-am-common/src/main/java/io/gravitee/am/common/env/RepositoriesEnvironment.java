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
package io.gravitee.am.common.env;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;

@RequiredArgsConstructor
public class RepositoriesEnvironment {
    private final Environment environment;

    public String getProperty(String key) {
        String property = environment.getProperty(key);
        if (property == null && canFallback(key)) {
            return environment.getProperty(fallback(key));
        } else {
            return property;
        }
    }

    public String getProperty(String key, String defaultValue) {
        String property = environment.getProperty(key);
        if (property == null) {
            if(canFallback(key)){
                return environment.getProperty(fallback(key), defaultValue);
            } else {
                return defaultValue;
            }
        } else {
            return property;
        }
    }

    public <T> T getProperty(String key, Class<T> targetType) {
        T property = environment.getProperty(key, targetType);
        if (property == null && canFallback(key)) {
            return environment.getProperty(fallback(key), targetType);
        } else {
            return property;
        }
    }

    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        T property = environment.getProperty(key, targetType);
        if (property == null) {
            if(canFallback(key)){
                return environment.getProperty(fallback(key), targetType, defaultValue);
            } else {
                return defaultValue;
            }
        } else {
            return property;
        }

    }

    private boolean canFallback(String key) {
        return key.matches("^repositories\\.(gateway|management|oauth2|ratelimit)\\..*");
    }

    private String fallback(String key) {
         if (key.startsWith("repositories.gateway.")) {
            return key.replaceFirst("repositories\\.gateway\\.", "oauth2.");
        }
        if (key.startsWith("repositories.management.")) {
            return key.replaceFirst("repositories\\.management\\.", "management.");
        }
        if (key.startsWith("repositories.oauth2.")) {
            return key.replaceFirst("repositories\\.oauth2\\.", "oauth2.");
        }
        if (key.startsWith("repositories.ratelimit.")) {
            return key.replaceFirst("repositories\\.ratelimit\\.", "gateway.");
        }
        return key;
    }

}

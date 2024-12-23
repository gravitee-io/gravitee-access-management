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
package io.gravitee.am.common.plugin;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;

@Slf4j
public record ValidationResult(boolean succeeded, String failedMessage, Map<String, Object> additionalInformation){
    public static final ValidationResult SUCCEEDED = new ValidationResult(true, null, Map.of());
    public boolean failed(){
        return !succeeded;
    }

    public <T> Optional<T> getAdditionalInformation(String key, Class<T> clazz){
        try {
            return Optional.ofNullable(clazz.cast(additionalInformation.get(key)));
        } catch (Exception e){
            log.error("Incorrect class for key {}: {}", key, clazz);
            return Optional.empty();
        }
    }

    public static ValidationResult valid(Map<String, Object> additionalInformation){
        return new ValidationResult(true, null, additionalInformation);
    }

    public static ValidationResult valid(){
        return valid(Map.of());
    }

    public static ValidationResult invalid(String message){
        return new ValidationResult(false, message, Map.of());
    }
}

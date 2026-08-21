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
package io.gravitee.am.service.model;

import io.gravitee.am.model.KeyRetrievalSettings;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.utils.SetterUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
public class PatchKeyRetrievalSettings {

    private Optional<Boolean> allowUnsecuredHttpUri;
    private Optional<Boolean> allowPrivateIpAddress;
    private Optional<Integer> fetchTimeoutMs;
    private Optional<Integer> maxResponseSizeKb;
    private Optional<Integer> cacheTtlSeconds;
    private Optional<Integer> cacheMaxEntries;

    public KeyRetrievalSettings patch(KeyRetrievalSettings toPatch) {
        validate();
        KeyRetrievalSettings result = toPatch != null ? toPatch : KeyRetrievalSettings.defaultSettings();
        SetterUtils.safeSet(result::setAllowUnsecuredHttpUri, this.getAllowUnsecuredHttpUri(), boolean.class);
        SetterUtils.safeSet(result::setAllowPrivateIpAddress, this.getAllowPrivateIpAddress(), boolean.class);
        Optional.ofNullable(getFetchTimeoutMs())
                .ifPresent(opt -> result.setFetchTimeoutMs(opt.orElse(KeyRetrievalSettings.DEFAULT_FETCH_TIMEOUT_MS)));
        Optional.ofNullable(getMaxResponseSizeKb())
                .ifPresent(opt -> result.setMaxResponseSizeKb(opt.orElse(KeyRetrievalSettings.DEFAULT_MAX_RESPONSE_SIZE_KB)));
        Optional.ofNullable(getCacheTtlSeconds())
                .ifPresent(opt -> result.setCacheTtlSeconds(opt.orElse(KeyRetrievalSettings.DEFAULT_CACHE_TTL_SECONDS)));
        Optional.ofNullable(getCacheMaxEntries())
                .ifPresent(opt -> result.setCacheMaxEntries(opt.orElse(KeyRetrievalSettings.DEFAULT_CACHE_MAX_ENTRIES)));
        return result;
    }

    private void validate() {
        requirePositive("fetchTimeoutMs", fetchTimeoutMs);
        requirePositive("maxResponseSizeKb", maxResponseSizeKb);
        requirePositive("cacheTtlSeconds", cacheTtlSeconds);
        requirePositive("cacheMaxEntries", cacheMaxEntries);
    }

    private static void requirePositive(String field, Optional<Integer> value) {
        if (value != null && value.isPresent() && value.get() <= 0) {
            throw new InvalidParameterException(field + " must be a positive integer");
        }
    }
}

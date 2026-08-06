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
package io.gravitee.am.repository.jdbc.common;

import lombok.Value;

/**
 * @author GraviteeSource Team
 */
@Value
public class RetryOnConcurrencyFailureConfiguration {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_INITIAL_DELAY_MS = 50;

    int maxAttempts;
    long initialDelayMs;

    public static RetryOnConcurrencyFailureConfiguration defaultCfg(){
        return new RetryOnConcurrencyFailureConfiguration(DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_DELAY_MS);
    }
}

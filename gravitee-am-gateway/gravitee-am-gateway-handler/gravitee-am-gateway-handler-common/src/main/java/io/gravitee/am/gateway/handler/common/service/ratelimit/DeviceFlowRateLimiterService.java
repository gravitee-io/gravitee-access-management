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
package io.gravitee.am.gateway.handler.common.service.ratelimit;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Bounds how often one account can get a device flow user code wrong, so that an account cannot be
 * used to guess the codes outstanding on a domain.
 *
 * The bucket is keyed on the user and the domain they belong to, never on the application: an
 * account out of attempts must not get a fresh budget by starting the flow from another one.
 *
 * @author GraviteeSource Team
 */
public interface DeviceFlowRateLimiterService {

    /**
     * What the bucket of a device flow code entry is discriminated by, so that it never shares a
     * bucket with the multi-factor authentication attempts of the same user.
     */
    String PURPOSE = "DEVICE_CODE_ENTRY";

    boolean isRateLimitEnabled();

    /**
     * Whether this user may still submit a code, without spending anything to find out.
     */
    Single<Boolean> hasAttemptsLeft(String userId, String domainId);

    /**
     * Spend one of the attempts this user has, once one of theirs turned out to be wrong.
     */
    Completable recordWrongAttempt(String userId, String domainId);
}

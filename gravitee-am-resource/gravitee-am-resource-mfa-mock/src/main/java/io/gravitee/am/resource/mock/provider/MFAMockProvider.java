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
package io.gravitee.am.resource.mock.provider;

import io.gravitee.am.common.exception.mfa.InvalidCodeException;
import io.gravitee.am.resource.api.mfa.MFAChallenge;
import io.gravitee.am.resource.api.mfa.MFALink;
import io.gravitee.am.resource.api.mfa.MFAResourceProvider;
import io.gravitee.am.resource.mock.MFAResourceConfiguration;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class MFAMockProvider implements MFAResourceProvider {

    @Autowired
    private MFAResourceConfiguration configuration;

    @Override
    public Completable send(MFALink target) {
        log.info("MFAMockProvider: SEND CODE {}", configuration.getCode());
        return Completable.complete();
    }

    @Override
    public Completable verify(MFAChallenge challenge) {
        log.info("MFAMockProvider: VERIFY CODE {}", configuration.getCode());
        if (!configuration.getCode().equals(challenge.getCode())) {
            return Completable.error(new InvalidCodeException("Invalid 2FA code"));
        }
        return Completable.complete();
    }
}

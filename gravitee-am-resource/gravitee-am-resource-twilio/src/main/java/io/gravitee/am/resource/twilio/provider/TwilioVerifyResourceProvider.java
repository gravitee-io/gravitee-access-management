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
package io.gravitee.am.resource.twilio.provider;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import io.gravitee.am.common.exception.mfa.InvalidCodeException;
import io.gravitee.am.common.exception.mfa.SendChallengeException;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.am.resource.api.mfa.MFAChallenge;
import io.gravitee.am.resource.api.mfa.MFALink;
import io.gravitee.am.resource.api.mfa.MFAResourceProvider;
import io.gravitee.am.resource.twilio.TwilioVerifyResourceConfiguration;
import io.reactivex.Completable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TwilioVerifyResourceProvider implements MFAResourceProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwilioVerifyResourceProvider.class);
    public static final String APPROVED = "approved";

    @Autowired
    private TwilioVerifyResourceConfiguration configuration;

    @Override
    public ResourceProvider start() throws Exception {
        Twilio.init(configuration.getAccountSid(), configuration.getAuthToken());
        return this;
    }

    @Override
    public ResourceProvider stop() throws Exception {
        Twilio.destroy();
        return this;
    }

    @Override
    public Completable send(MFALink target) {
        String channel;
        switch (target.getChannel()) {
            case SMS:
                channel = "sms";
                break;
            case EMAIL:
                channel = "email";
                break;
            default:
                return Completable.error(new IllegalArgumentException("Unsupported verification channel '" + target.getChannel() + "'"));
        }

        return Completable.create((emitter) -> {
            try {
                Verification verification = Verification.creator(
                        configuration.getSid(),
                        target.getTarget(),
                        channel)
                        .create();

                LOGGER.debug("Twilio Verification code asked with ID '{}'", verification.getSid());
                emitter.onComplete();
            } catch (ApiException e) {
                LOGGER.error("Challenge emission fails", e);
                emitter.onError(new SendChallengeException("Unable to send challenge"));
            }
        });
    }

    @Override
    public Completable verify(MFAChallenge challenge) {
        return Completable.create((emitter) -> {
            try {
                VerificationCheck verification = VerificationCheck.creator(configuration.getSid(), challenge.getCode())
                        .setTo(challenge.getTarget())
                        .create();

                LOGGER.debug("Twilio Verification code with ID '{}' verified with status '{}'", verification.getSid(), verification.getStatus());
                if (!APPROVED.equalsIgnoreCase(verification.getStatus())) {
                    emitter.onError(new InvalidCodeException("Invalid 2FA Code"));
                }
                emitter.onComplete();
            } catch (ApiException e) {
                LOGGER.error("Challenge verification fails", e);
                emitter.onError(new InvalidCodeException("Invalid 2FA Code"));
            }
        });
    }
}

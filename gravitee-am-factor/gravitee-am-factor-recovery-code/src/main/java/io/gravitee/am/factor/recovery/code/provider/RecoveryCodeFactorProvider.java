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

package io.gravitee.am.factor.recovery.code.provider;

import io.gravitee.am.common.exception.mfa.InvalidCodeException;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.factor.api.RecoveryFactor;
import io.gravitee.am.factor.recovery.code.RecoveryCodeFactorConfiguration;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.reactivex.Completable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.common.factor.FactorSecurityType.RECOVERY_CODE;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RecoveryCodeFactorProvider implements FactorProvider, RecoveryFactor {
    private static final Logger logger = LoggerFactory.getLogger(RecoveryCodeFactorProvider.class);

    @Autowired
    private RecoveryCodeFactorConfiguration configuration;

    @Override
    public Completable sendChallenge(FactorContext context) {
        return Completable.complete();
    }

    @Override
    public Completable verify(FactorContext context) {
        final String code = context.getData(FactorContext.KEY_CODE, String.class);
        final EnrolledFactor enrolledFactor = context.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class);
        final List<String> recoveryCodes = (List<String>) enrolledFactor.getSecurity().getAdditionalData().get(RECOVERY_CODE);

        return Completable.create(emitter -> {
            if (recoveryCodes.contains(code)) {
                //remove the code from the list as the recovery is not re-usable
                recoveryCodes.remove(code);
                enrolledFactor.getSecurity().setAdditionalData(Map.of(RECOVERY_CODE, recoveryCodes));
                emitter.onComplete();
            } else {
                emitter.onError(new InvalidCodeException("Invalid recovery code"));
            }
        });
    }

    @Override
    public Single<Enrollment> enroll(String account) {
        //AccountFactorsEndpointHandler#buildEnrolledFactor uses the Enrollment 'key' in EnrolledFactorSecurity
        //for factors other than this recovery code factor. Hence, the key is set to empty string.
        return Single.just(new Enrollment(""));
    }

    @Override
    public boolean checkSecurityFactor(EnrolledFactor securityFactor) {
        return true;
    }

    @Override
    public boolean needChallengeSending() {
        return true;
    }

    @Override
    public boolean useVariableFactorSecurity() {
        return true;
    }

    @Override
    public Single<EnrolledFactorSecurity> generateRecoveryCode(FactorContext context) {
        final Factor recoveryFactor = context.getData(FactorContext.KEY_RECOVERY_FACTOR, Factor.class);
        final EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId(recoveryFactor.getId());
        enrolledFactor.setStatus(FactorStatus.PENDING_ACTIVATION);
        enrolledFactor.setCreatedAt(new Date());
        enrolledFactor.setUpdatedAt(enrolledFactor.getCreatedAt());

        return addRecoveryCodeFactor(context, enrolledFactor);
    }

    private Single<EnrolledFactorSecurity> addRecoveryCodeFactor(FactorContext context, EnrolledFactor enrolledFactor) {
        try {
            final UserService userService = context.getComponent(UserService.class);
            final EnrolledFactorSecurity enrolledFactorSecurity = createEnrolledFactorSecurity();
            enrolledFactor.setSecurity(enrolledFactorSecurity);

            return userService
                    .addFactor(context.getUser().getId(), enrolledFactor, new DefaultUser(context.getUser()))
                    .map(__ -> enrolledFactorSecurity);
        } catch (Exception exception) {
            return Single.error(exception);
        }
    }

    private EnrolledFactorSecurity createEnrolledFactorSecurity() {
        final Map<String, Object> recoveryCode = Map.of(RECOVERY_CODE, recoveryCodes());
        return new EnrolledFactorSecurity(RECOVERY_CODE, Integer.toString(configuration.getDigit()), recoveryCode);
    }

    private List<String> recoveryCodes() {
        final int length = configuration.getDigit();
        final int count = configuration.getCount();
        if (length <= 0 || count <= 0) {
            throw new IllegalArgumentException("Configuration cannot be used for recovery code. Either number of digits or number of recovery code is 0 or negative.");
        }
        logger.debug("Generating recovery code of {} digits", length);
        return SecureRandomString.randomAlphaNumeric(length, count);
    }
}

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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.gravitee.am.common.utils.ConstantKeys;
import io.vertx.rxjava3.ext.web.Session;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.gravitee.am.common.utils.ConstantKeys.ALTERNATIVE_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.AUTH_FLOW_CONTEXT_VERSION_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ALREADY_EXISTS_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.LOGIN_ATTEMPT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_ENROLL_CONDITIONAL_SKIPPED_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_FORCE_ENROLLMENT;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_STOP;
import static io.gravitee.am.common.utils.ConstantKeys.PASSWORDLESS_AUTH_ACTION_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.RISK_ASSESSMENT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.STRONG_AUTH_COMPLETED_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_IP_LOCATION;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_USER_AGENT;
import static io.gravitee.am.common.utils.ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY;

public class AmSession extends TypesafeMapAdapter {
    private static final List<String> EVALUABLE_ATTRIBUTES =
            Arrays.asList(
                    RISK_ASSESSMENT_KEY,
                    MFA_ENROLLMENT_COMPLETED_KEY,
                    ENROLLED_FACTOR_ID_KEY,
                    MFA_CHALLENGE_COMPLETED_KEY,
                    STRONG_AUTH_COMPLETED_KEY,
                    WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY,
                    PASSWORDLESS_AUTH_ACTION_KEY);

    private final Session delegate;

    public AmSession(Session delegate) {
        super(delegate::get);
        this.delegate = delegate;
    }

    public boolean isUserStronglyAuth() {
        return getBoolean(ConstantKeys.STRONG_AUTH_COMPLETED_KEY);
    }

    public Map<String, Object> getEvaluableAttributes() {
        return EVALUABLE_ATTRIBUTES.stream()
                .filter(x -> delegate.get(x) != null)
                .collect(Collectors.toMap(key -> key, delegate::get));
    }

    public AmSession setAuthFlowContextVersion(int version) {
        delegate.put(AUTH_FLOW_CONTEXT_VERSION_KEY, version);
        return this;
    }

    public AmSession setForceMfaEnrollment(boolean value) {
        delegate.put(MFA_FORCE_ENROLLMENT, value);
        return this;
    }

    public AmSession setAlternativeFactorId(String factorId) {
        delegate.put(ALTERNATIVE_FACTOR_ID_KEY, factorId);
        return this;
    }

    public boolean deviceAlreadyExists() {
        return getBoolean(DEVICE_ALREADY_EXISTS_KEY);
    }

    public boolean isMfaChallengeCompleted() {
        return getBoolean(MFA_CHALLENGE_COMPLETED_KEY);
    }

    public String getEnrolledFactorId() {
        return typesafeGet(ENROLLED_FACTOR_ID_KEY, String.class);
    }

    public void setMfaStop(boolean value) {
        delegate.put(MFA_STOP, value);
    }

    public boolean isMfaFlowStopped() {
        return getBoolean(MFA_STOP);
    }

    public void setMfaEnrollmentCanBeSkippedConditionally(boolean value) {
        delegate.put(MFA_ENROLL_CONDITIONAL_SKIPPED_KEY, value);
    }

    public boolean canMfaEnrollmentBeSkippedConditionally() {
        return getBoolean(MFA_ENROLL_CONDITIONAL_SKIPPED_KEY);
    }

    public int getLoginAttempt() {
        return getInt(LOGIN_ATTEMPT_KEY);
    }


    public AmSession setIpLocationConsent(boolean value) {
        return setIpLocationConsent(value, true);
    }
    public AmSession setIpLocationConsent(boolean value, boolean overrideExisting) {
        return setWithOptionalOverride(USER_CONSENT_IP_LOCATION, value, overrideExisting);
    }

    public AmSession setUserAgentConsent(boolean value) {
        return setUserAgentConsent(value, true);
    }
    public AmSession setUserAgentConsent(boolean value, boolean overrideExistingValue) {
        return setWithOptionalOverride(USER_CONSENT_USER_AGENT, value, overrideExistingValue);
    }

    private <T> AmSession setWithOptionalOverride(String key, T value, boolean overrideExistingValue) {
        if (overrideExistingValue) {
            delegate.put(key, value);
        } else {
            delegate.putIfAbsent(key, value);
        }
        return this;
    }
}

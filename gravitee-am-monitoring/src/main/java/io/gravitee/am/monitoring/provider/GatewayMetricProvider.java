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
package io.gravitee.am.monitoring.provider;

import io.gravitee.am.monitoring.metrics.Constants;
import io.gravitee.am.monitoring.metrics.CounterHelper;
import io.gravitee.am.monitoring.metrics.GaugeHelper;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import static io.gravitee.am.monitoring.metrics.Constants.METRICS_APP;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_APP_EVENTS;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_AUTH_EVENTS;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_CONSENTS;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_ERRORS;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_IDPS;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_IDP_EVENTS;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_INTROSPECTS;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_LOGOUT;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_MFA_CHALLENGES;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_MFA_ENROLLMENTS;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_PROTECTED_RESOURCES;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_PROTECTED_RESOURCE_EVENTS;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_REVOKE;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_TOKENS;
import static io.gravitee.am.monitoring.metrics.Constants.METRICS_USER_INFO;
import static io.gravitee.am.monitoring.metrics.Constants.TAG_AUTH_IDP;
import static io.gravitee.am.monitoring.metrics.Constants.TAG_AUTH_STATUS;
import static io.gravitee.am.monitoring.metrics.Constants.TAG_STATUS;
import static io.gravitee.am.monitoring.metrics.Constants.TAG_VALUE_AUTH_IDP_EXTERNAL;
import static io.gravitee.am.monitoring.metrics.Constants.TAG_VALUE_AUTH_IDP_INTERNAL;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GatewayMetricProvider {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILURE = "FAILURE";
    private final CounterHelper appEvtCounter = new CounterHelper(METRICS_APP_EVENTS);

    private final GaugeHelper appGauge = new GaugeHelper(METRICS_APP);

    private final CounterHelper idpEvtCounter = new CounterHelper(METRICS_IDP_EVENTS);

    private final GaugeHelper idpGauge = new GaugeHelper(METRICS_IDPS);

    private final CounterHelper protectedResourceEvtCounter = new CounterHelper(METRICS_PROTECTED_RESOURCE_EVENTS);

    private final GaugeHelper protectedResourceGauge = new GaugeHelper(METRICS_PROTECTED_RESOURCES);

    private final CounterHelper domainEvtCounter = new CounterHelper(Constants.METRICS_DOMAIN_EVENTS);

    private final GaugeHelper domainGauge = new GaugeHelper(Constants.METRICS_DOMAINS);

    private final GaugeHelper eventsGauge = new GaugeHelper(Constants.METRICS_EVENTS_SYNC);

    private final GaugeHelper successfulStagingEmails = new GaugeHelper(Constants.METRICS_PROCESSED_STAGING_EMAILS, Tags.of(
            Tag.of(TAG_STATUS, STATUS_SUCCESS)));

    private final GaugeHelper failureStagingEmails = new GaugeHelper(Constants.METRICS_PROCESSED_STAGING_EMAILS, Tags.of(
            Tag.of(TAG_STATUS, STATUS_FAILURE)));

    private final GaugeHelper droppedEmails = new GaugeHelper(Constants.METRICS_DROPPED_EMAILS);

    private final CounterHelper internalSuccessfulAuth = new CounterHelper(METRICS_AUTH_EVENTS, Tags.of(
            Tag.of(TAG_AUTH_STATUS, "SUCCESS"),
            Tag.of(TAG_AUTH_IDP, TAG_VALUE_AUTH_IDP_INTERNAL)));

    private final CounterHelper internalFailedAuth = new CounterHelper(METRICS_AUTH_EVENTS, Tags.of(
            Tag.of(TAG_AUTH_STATUS, "FAILURE"),
            Tag.of(TAG_AUTH_IDP, TAG_VALUE_AUTH_IDP_INTERNAL)));

    private final CounterHelper socialSuccessfulAuth = new CounterHelper(METRICS_AUTH_EVENTS, Tags.of(
            Tag.of(TAG_AUTH_STATUS, "SUCCESS"),
            Tag.of(TAG_AUTH_IDP, TAG_VALUE_AUTH_IDP_EXTERNAL)));

    private final CounterHelper socialFailedAuth = new CounterHelper(METRICS_AUTH_EVENTS, Tags.of(
            Tag.of(TAG_AUTH_STATUS, "FAILURE"),
            Tag.of(TAG_AUTH_IDP, TAG_VALUE_AUTH_IDP_EXTERNAL)));

    private final CounterHelper introspectSuccess = new CounterHelper(METRICS_INTROSPECTS, Tags.of(
            Tag.of(TAG_STATUS, "SUCCESS")));

    private final CounterHelper introspectFailure = new CounterHelper(METRICS_INTROSPECTS, Tags.of(
            Tag.of(TAG_STATUS, "FAILURE")));

    private final CounterHelper tokenSuccess = new CounterHelper(METRICS_TOKENS, Tags.of(
            Tag.of(TAG_STATUS, "SUCCESS")));

    private final CounterHelper tokenFailure = new CounterHelper(METRICS_TOKENS, Tags.of(
            Tag.of(TAG_STATUS, "FAILURE")));

    private final CounterHelper revokeSuccess = new CounterHelper(METRICS_REVOKE, Tags.of(
            Tag.of(TAG_STATUS, "SUCCESS")));

    private final CounterHelper revokeFailure = new CounterHelper(METRICS_REVOKE, Tags.of(
            Tag.of(TAG_STATUS, "FAILURE")));

    private final CounterHelper consentSuccess = new CounterHelper(METRICS_CONSENTS, Tags.of(
            Tag.of(TAG_STATUS, "SUCCESS")));

    private final CounterHelper consentFailure = new CounterHelper(METRICS_CONSENTS, Tags.of(
            Tag.of(TAG_STATUS, "FAILURE")));

    private final CounterHelper errorFailure = new CounterHelper(METRICS_ERRORS);

    private final CounterHelper userInfoSuccess = new CounterHelper(METRICS_USER_INFO, Tags.of(
            Tag.of(TAG_STATUS, "SUCCESS")));

    private final CounterHelper userInfoFailure = new CounterHelper(METRICS_USER_INFO, Tags.of(
            Tag.of(TAG_STATUS, "FAILURE")));

    private final CounterHelper logoutSuccess = new CounterHelper(METRICS_LOGOUT, Tags.of(
            Tag.of(TAG_STATUS, "SUCCESS")));

    private final CounterHelper logoutFailure = new CounterHelper(METRICS_LOGOUT, Tags.of(
            Tag.of(TAG_STATUS, "FAILURE")));

    private final CounterHelper mfaSuccess = new CounterHelper(METRICS_MFA_CHALLENGES, Tags.of(
            Tag.of(TAG_STATUS, "SUCCESS")));

    private final CounterHelper mfaFailure = new CounterHelper(METRICS_MFA_CHALLENGES, Tags.of(
            Tag.of(TAG_STATUS, "FAILURE")));

    private final CounterHelper mfaEnrollmentSuccess = new CounterHelper(METRICS_MFA_ENROLLMENTS, Tags.of(
            Tag.of(TAG_STATUS, "SUCCESS")));

    private final CounterHelper mfaEnrollmentFailure = new CounterHelper(METRICS_MFA_ENROLLMENTS, Tags.of(
            Tag.of(TAG_STATUS, "FAILURE")));

    public void incrementAppEvt() {
        this.appEvtCounter.increment();
    }

    public void incrementApp() {
        this.appGauge.incrementValue();
    }

    public void decrementApp() {
        this.appGauge.decrementValue();
    }

    public void incrementIdpEvt() {
        this.idpEvtCounter.increment();
    }

    public void incrementIdp() {
        this.idpGauge.incrementValue();
    }

    public void decrementIdp() {
        this.idpGauge.decrementValue();
    }

    public void incrementProtectedResourceEvt() {
        this.protectedResourceEvtCounter.increment();
    }

    public void incrementProtectedResource() {
        this.protectedResourceGauge.incrementValue();
    }

    public void decrementProtectedResource() {
        this.protectedResourceGauge.decrementValue();
    }

    public void incrementDomainEvt() {
        this.domainEvtCounter.increment();
    }

    public void incrementDomain() {
        this.domainGauge.incrementValue();
    }

    public void decrementDomain() {
        this.domainGauge.decrementValue();
    }

    public void incrementProcessedStagingEmails(boolean success) {
        if (success) {
            this.successfulStagingEmails.incrementValue();
        } else {
            this.failureStagingEmails.incrementValue();
        }
    }

    public void incrementDroppedEmails() {
        this.droppedEmails.incrementValue();
    }

    public void updateSyncEvents(int evts) {
        this.eventsGauge.updateValue(evts);
    }

    public void incrementSuccessfulAuth(boolean externalIdp) {
        if (externalIdp) {
            this.socialSuccessfulAuth.increment();
        } else {
            this.internalSuccessfulAuth.increment();
        }
    }

    public void incrementFailedAuth(boolean externalIdp) {
        if (externalIdp) {
            this.socialFailedAuth.increment();
        } else {
            this.internalFailedAuth.increment();
        }
    }

    public void incrementError() {
        this.errorFailure.increment();
    }

    public void incrementLogoutSuccess() {
        this.logoutSuccess.increment();
    }

    public void incrementLogoutFailure() {
        this.logoutFailure.increment();
    }

    public void incrementUserInfoSuccess() {
        this.userInfoSuccess.increment();
    }

    public void incrementUserInfoFailure() {
        this.userInfoFailure.increment();
    }

    public void incrementIntrospectSuccess() {
        this.introspectSuccess.increment();
    }

    public void incrementIntrospectFailure() {
        this.introspectFailure.increment();
    }

    public void incrementTokenSuccess() {
        this.tokenSuccess.increment();
    }

    public void incrementTokenFailure() {
        this.tokenFailure.increment();
    }

    public void incrementRevokeSuccess() {
        this.revokeSuccess.increment();
    }

    public void incrementRevokeFailure() {
        this.revokeFailure.increment();
    }

    public void incrementConsentSuccess() {
        this.consentSuccess.increment();
    }

    public void incrementConsentFailure() {
        this.consentFailure.increment();
    }

    public void incrementMfaSuccess() {
        this.mfaSuccess.increment();
    }

    public void incrementMfaFailure() {
        this.mfaFailure.increment();
    }

    public void incrementMfaEnrollmentSuccess() {
        this.mfaEnrollmentSuccess.increment();
    }

    public void incrementMfaEnrollmentFailure() {
        this.mfaEnrollmentFailure.increment();
    }
}

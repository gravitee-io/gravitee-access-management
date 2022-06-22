/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.handler.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.gateway.handler.common.auth.AuthenticationDetails;
import io.gravitee.am.model.Device;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserActivity;
import io.gravitee.am.service.DeviceService;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.risk.assessment.api.assessment.Assessment;
import io.gravitee.risk.assessment.api.assessment.AssessmentMessage;
import io.gravitee.risk.assessment.api.assessment.AssessmentMessageResult;
import io.gravitee.risk.assessment.api.assessment.AssessmentResult;
import io.gravitee.risk.assessment.api.assessment.data.AssessmentData;
import io.gravitee.risk.assessment.api.assessment.settings.AssessmentSettings;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import io.gravitee.risk.assessment.api.devices.Devices;
import io.gravitee.risk.assessment.api.geovelocity.GeoTimeCoordinate;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.vertx.core.AsyncResult;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ID;
import static io.gravitee.risk.assessment.api.assessment.Assessment.NONE;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * Enables calculation of risk assessment for a given user.
 */
@Component
public class RiskAssessmentHelper {

    private static final Logger logger = LoggerFactory.getLogger(RiskAssessmentHelper.class);
    private static final String RISK_ASSESSMENT_SERVICE = "service:risk-assessment";
    private static final Map<String, BiConsumer<RiskAssessmentSettings, AssessmentSettings>> assessments =
            Map.of("ipReputation", RiskAssessmentSettings::setIpReputationAssessment,
                    "geoVelocity", RiskAssessmentSettings::setGeoVelocityAssessment,
                    "device", RiskAssessmentSettings::setDeviceAssessment);
    private final DeviceService deviceService;
    private final Environment environment;
    private final UserActivityService userActivityService;
    private final ObjectMapper objectMapper;
    private final EventBus eventBus;

    public RiskAssessmentHelper(DeviceService deviceService, Environment environment, UserActivityService userActivityService, ObjectMapper objectMapper, Vertx vertx) {
        this.deviceService = deviceService;
        this.environment = environment;
        this.userActivityService = userActivityService;
        this.objectMapper = objectMapper;
        this.eventBus = vertx.eventBus();
    }

    public void computeRiskAssessment(AuthenticationDetails authenticationDetails, Consumer<AssessmentMessageResult> resultConsumer, RiskAssessmentSettings riskAssessment) {
        var assessmentMessage = Single.just(new AssessmentMessage().setSettings(riskAssessment).setData(new AssessmentData()));
        Domain domain = authenticationDetails.getDomain();
        User user = authenticationDetails.getUser();
        assessmentMessage
                .flatMap(buildDeviceAssessmentMessage(authenticationDetails, riskAssessment, domain, user))
                .flatMap(buildIpReputationMessage(authenticationDetails, riskAssessment))
                .flatMap(buildGeoVelocityMessage(domain, user, riskAssessment))
                .doOnSuccess(processedMessage -> consumeRiskAssessmentResult(processedMessage, resultConsumer))
                .doOnError(throwable -> logger.error("An unexpected error has occurred while trying to apply risk assessment: ", throwable))
                .subscribe();

    }

    public RiskAssessmentSettings getRiskAssessmentSettings() {
        var settings = new RiskAssessmentSettings();
        assessments.forEach((assessmentType, setter) -> {
            var enabled = environment.getProperty(String.format("alerts.risk_assessment.settings.%s.enabled", assessmentType), Boolean.class, false);
            AssessmentSettings assessmentSettings = new AssessmentSettings().setEnabled(enabled);
            if (enabled) {
                settings.setEnabled(true);
                Map<Assessment, Double> assessmentThresholds = new HashMap<>();
                for (Assessment assessment : Assessment.values()) {
                    Double threshold = environment.getProperty(String.format("alerts.risk_assessment.settings.%s.thresholds.%s", assessmentType, assessment), Double.class);
                    if (threshold != null) {
                        assessmentThresholds.put(assessment, threshold);
                    }
                }
                assessmentSettings.setThresholds(assessmentThresholds);
            }
            setter.accept(settings, assessmentSettings);
        });
        return settings;
    }

    private Function<AssessmentMessage, Single<AssessmentMessage>> buildDeviceAssessmentMessage(
            AuthenticationDetails authDetails,
            RiskAssessmentSettings riskAssessment,
            Domain domain,
            User user) {
        return assessmentMessage -> {
            var deviceAssessment = ofNullable(riskAssessment.getDeviceAssessment()).orElse(new AssessmentSettings());
            if (deviceAssessment.isEnabled()) {
                logger.debug("Decorating assessment with devices");
                return deviceService.findByDomainAndUser(domain.getId(), user.getId())
                        .map(Device::getDeviceId)
                        .toList().flatMap(deviceIds -> {
                            assessmentMessage.getData().setDevices(new Devices()
                                    .setKnownDevices(deviceIds)
                                    .setEvaluatedDevice((String) authDetails.getPrincipal().getContext().get(DEVICE_ID))
                            );
                            return Single.just(assessmentMessage);
                        });
            }
            return Single.just(assessmentMessage);
        };
    }

    private Function<AssessmentMessage, Single<AssessmentMessage>> buildIpReputationMessage(
            AuthenticationDetails authDetails,
            RiskAssessmentSettings riskAssessment) {
        return assessmentMessage -> {
            var ipReputationAssessment = ofNullable(riskAssessment.getIpReputationAssessment()).orElse(new AssessmentSettings());
            if (ipReputationAssessment.isEnabled()) {
                logger.debug("Decorating assessment with IP reputation");
                String ip = (String) authDetails.getPrincipal().getContext().get(Claims.ip_address);
                assessmentMessage.getData().setCurrentIp(ip);
            }
            return Single.just(assessmentMessage);
        };
    }

    private Function<AssessmentMessage, Single<AssessmentMessage>> buildGeoVelocityMessage(
            Domain domain,
            User user,
            RiskAssessmentSettings riskAssessment) {
        return assessmentMessage -> {
            var geoVelocityAssessment = ofNullable(riskAssessment.getGeoVelocityAssessment()).orElse(new AssessmentSettings());
            if (geoVelocityAssessment.isEnabled()) {
                return userActivityService.findByDomainAndTypeAndUserAndLimit(domain.getId(), UserActivity.Type.LOGIN, user.getId(), 2)
                        .toList()
                        .flatMap(activityList -> {
                            assessmentMessage.getData().setGeoTimeCoordinates(computeGeoTimeCoordinates(activityList));
                            return Single.just(assessmentMessage);
                        });
            }
            return Single.just(assessmentMessage);
        };
    }

    private List<GeoTimeCoordinate> computeGeoTimeCoordinates(List<UserActivity> activityList) {
        return activityList.stream().filter(ua -> nonNull(ua.getLatitude())).filter(ua -> nonNull(ua.getLongitude()))
                .map(ua -> new GeoTimeCoordinate(
                        ua.getLatitude(),
                        ua.getLongitude(),
                        ua.getCreatedAt().toInstant().getEpochSecond())
                ).collect(toList());
    }

    private void consumeRiskAssessmentResult(AssessmentMessage message, Consumer<AssessmentMessageResult> resultConsumer) throws JsonProcessingException {
        eventBus.<String>request(RISK_ASSESSMENT_SERVICE, objectMapper.writeValueAsString(message), response -> {
            if (response.succeeded()) {
                try {
                    resultConsumer.accept(extractMessageResult(response));
                } catch (Exception e) {
                    logger.error("Error processing risk assessment response.", e);
                }
            } else if (response.failed()) {
                logger.warn("{} could not be called, reason: {}", RISK_ASSESSMENT_SERVICE, response.cause().getMessage());
                logger.debug("", response.cause());
            }
        });
    }

    private AssessmentMessageResult extractMessageResult(AsyncResult<Message<String>> response) {
        try {
            return objectMapper.readValue(response.result().body(), AssessmentMessageResult.class);
        } catch (JsonProcessingException e) {
            logger.error("An unexpected error has occurred: ", e);
            return new AssessmentMessageResult()
                    .setDevices(new AssessmentResult<Double>().setAssessment(NONE))
                    .setGeoVelocity(new AssessmentResult<Double>().setAssessment(NONE))
                    .setIpReputation(new AssessmentResult<Double>().setAssessment(NONE));
        }
    }
}

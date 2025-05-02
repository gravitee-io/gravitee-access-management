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
package io.gravitee.am.management.service.impl.notifications;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

@Configuration
public class NotifierSettingsResolver {

    public static final String DEFAULT_CERTIFICATE_EXPIRY_THRESHOLDS = "20,15,10,5,1";


    @Bean
    @Qualifier("certificateNotifierSettings")
    public NotifierSettings certificateNotifierSettings(Environment environment) {
        final String mainPrefix = "services.notifier.certificate.";
        final String fallbackPrefix = "services.certificate.";

        return getNotifierSettings(environment, mainPrefix, fallbackPrefix);
    }

    @Bean
    @Qualifier("clientSecretNotifierSettings")
    public NotifierSettings clientSecretNotifierSettings(Environment environment) {
        final String mainPrefix = "services.notifier.client-secret.";
        final String fallbackPrefix = "services.client-secret.";

        return getNotifierSettings(environment, mainPrefix, fallbackPrefix);
    }

    private NotifierSettings getNotifierSettings(Environment environment, String mainPrefix, String fallbackPrefix) {
        Boolean enabled = ofNullable(environment.getProperty(mainPrefix + "enabled", Boolean.class))
                .orElseGet(() -> environment.getProperty(fallbackPrefix + "enabled", Boolean.class, true));

        String cron = ofNullable(environment.getProperty(mainPrefix + "cronExpression", String.class))
                .orElseGet(() -> environment.getProperty(fallbackPrefix + "cronExpression", String.class, "0 0 5 * * *"));

        String thresholds = ofNullable(environment.getProperty(mainPrefix + "expiryThresholds", String.class))
                .orElseGet(() -> environment.getProperty(fallbackPrefix + "expiryThresholds", String.class, "20,15,10,5,1"));

        String emailSubject = ofNullable(environment.getProperty(mainPrefix + "expiryEmailSubject", String.class))
                .orElseGet(() -> environment.getProperty(fallbackPrefix + "expiryEmailSubject", String.class, "Expiration notification"));

        return new NotifierSettings(enabled, cron, parseThresholds(thresholds), emailSubject);
    }


    private List<Integer> parseThresholds(String expiryThresholds){
        if(expiryThresholds == null || expiryThresholds.isEmpty()){
            return List.of();
        }
        return Stream.of(expiryThresholds.trim().split(","))
                .map(String::trim)
                .map(Integer::valueOf)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

}

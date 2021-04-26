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
package io.gravitee.am.botdetection.google.recaptcha;

import io.gravitee.am.botdetection.api.BotDetectionConfiguration;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GoogleReCaptchaV3Configuration implements BotDetectionConfiguration {
    private String detectionType;
    private String siteKey;
    private String secretKey;
    private String serviceUrl;
    private float minScore;
    private String tokenParameterName;

    @Override
    public String getDetectionType() {
        return detectionType;
    }

    public void setDetectionType(String detectionType) {
        this.detectionType = detectionType;
    }

    public void setSiteKey(String siteKey) {
        this.siteKey = siteKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public void setMinScore(float minScore) {
        this.minScore = minScore;
    }

    public String getSiteKey() {
        return siteKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public float getMinScore() {
        return minScore;
    }

    public String getTokenParameterName() {
        return tokenParameterName;
    }

    public void setTokenParameterName(String tokenParameterName) {
        this.tokenParameterName = tokenParameterName;
    }
}

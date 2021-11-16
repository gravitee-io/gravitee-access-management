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
package io.gravitee.am.gateway.handler.ciba.ad.impl;

import io.gravitee.am.gateway.handler.ciba.ad.AuthenticationDeviceNotifier;
import io.gravitee.am.gateway.handler.ciba.ad.model.ADNotificationRequest;
import io.gravitee.am.gateway.handler.ciba.ad.model.ADNotificationResponse;
import io.gravitee.am.gateway.handler.ciba.exception.DeviceNotificationException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.http.WebClientBuilder;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.net.URL;

/**
 * This class is a client to request Authentication Device interaction to an external HTTP backend.
 * This backend will be responsible for the user authentication and will call AM back to inform about the
 * user choice (ACCEPTED or REJECTED) regarding the authentication request.
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationDeviceNotifierImpl implements AuthenticationDeviceNotifier, InitializingBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationDeviceNotifierImpl.class);
    public static final String TRANSACTION_ID = "tid";
    public static final String STATE = "state";
    public static final String PARAM_SUBJECT = "subject";
    public static final String PARAM_SCOPE = "scope";
    public static final String PARAM_EXPIRE = "expire";
    public static final String PARAM_ACR = "acr";
    public static final String PARAM_MESSAGE = "message";
    public static final String RESPONSE_ATTR_DATA = "data";
    public static final String URL_TO_GET_FROM_DOMAIN_SETTINGS = System.getProperty("ciba.ades", "http://localhost:8080/ciba/ades");

    @Autowired
    private Domain domain;

    @Autowired
    private Vertx vertx;

    @Autowired
    private WebClientBuilder clientBuilder;

    private WebClient client;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.client = this.clientBuilder.createWebClient(vertx, new URL(URL_TO_GET_FROM_DOMAIN_SETTINGS)); // TODO configure in domain ciba settings or in a plugin, for testing get values from sysprop
    }

    @Override
    public Single<ADNotificationResponse> notify(ADNotificationRequest request) {
        final MultiMap formData = MultiMap.caseInsensitiveMultiMap();

        formData.set(TRANSACTION_ID, request.getTransactionId());
        formData.set(STATE, request.getState());
        formData.set(PARAM_SUBJECT, request.getSubject());
        formData.set(PARAM_SCOPE, request.getScopes());
        formData.set(PARAM_EXPIRE, Integer.toString(request.getExpiresIn()));

        if (!CollectionUtils.isEmpty(request.getAcrValues())) {
            formData.set(PARAM_ACR, request.getAcrValues());
        }

        if (!StringUtils.isEmpty(request.getMessage())) {
            formData.set(PARAM_MESSAGE, request.getMessage());
        }

        return this.client.requestAbs(HttpMethod.POST, URL_TO_GET_FROM_DOMAIN_SETTINGS)
                .rxSendForm(formData)
                .doOnError((error) -> LOGGER.warn("Unexpected error during device notification : {}", error.getMessage(), error))
                .onErrorResumeNext(Single.error(new DeviceNotificationException("Unexpected error during device notification")))
                .map(response -> {
                    if (response.statusCode() != HttpStatusCode.OK_200) {
                        LOGGER.info("Device notification fails for tid '{}' with status '{}'", request.getTransactionId(), response.statusCode());
                        throw new DeviceNotificationException("Device notification fails");
                    }

                    final JsonObject result = response.bodyAsJsonObject();
                    if ( !request.getTransactionId().equals(result.getString(TRANSACTION_ID)) || !request.getState().equals(result.getString(STATE))) {
                        LOGGER.warn("Device notification response contains invalid tid or state", request.getTransactionId(), response.statusCode());
                        throw new DeviceNotificationException("Invalid device notification response");
                    }

                    final ADNotificationResponse notificationResponse = new ADNotificationResponse(request.getTransactionId());
                    if (result.containsKey(RESPONSE_ATTR_DATA)) {
                        notificationResponse.setExtraData(result.getJsonObject(RESPONSE_ATTR_DATA).getMap());
                    }
                    return notificationResponse;
        });
    }
}

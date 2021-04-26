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
package io.gravitee.am.botdetection.google.recaptcha.provider;

import io.gravitee.am.botdetection.api.BotDetectionContext;
import io.gravitee.am.botdetection.api.BotDetectionProvider;
import io.gravitee.am.botdetection.google.recaptcha.GoogleReCaptchaV3Configuration;
import io.gravitee.am.service.http.WebClientBuilder;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.URL;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GoogleReCaptchaV3Provider implements BotDetectionProvider  {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleReCaptchaV3Provider.class);

    @Autowired
    private Vertx vertx;

    @Autowired
    private GoogleReCaptchaV3Configuration configuration;

    private WebClient client;

    public void setClient(WebClient client) {
        this.client = client;
    }

    @Override
    public BotDetectionProvider start() throws Exception {
        this.setClient(new WebClientBuilder().createWebClient(vertx, new URL(configuration.getServiceUrl())));// TODO use version with exclude URL when available in master
        return this;
    }

    @Override
    public BotDetectionProvider stop() throws Exception {
        if (this.client != null) {
            this.client.close();
        }
        return this;
    }

    @Override
    public Single<Boolean> validate(BotDetectionContext context) {
        final String token = context.getHeader(configuration.getTokenParameterName()).orElse(context.getParameter(configuration.getTokenParameterName()).orElse(null));

        if (token == null || "".equals(token.trim())) {
            LOGGER.debug("Recaptcha token is empty");
            return Single.just(false);
        }

        return client.post(URI.create(configuration.getServiceUrl()).toString())
                .rxSendForm(MultiMap.caseInsensitiveMultiMap().set("secret", configuration.getSecretKey()).set("response", token))
                .map(buffer -> {

                    if (buffer.statusCode() != 200) {
                        LOGGER.error("An error occurred when trying to validate ReCaptcha token. (status={}/message={})", buffer.statusCode(), buffer.statusMessage());
                        return false;
                    }

                    final JsonObject response = buffer.bodyAsJsonObject();

                    Boolean success = response.getBoolean("success", false);
                    Double score = response.getDouble("score", 0.0d);

                    LOGGER.debug("ReCaptchaService success: {} score: {}", success, score);

                    // Result should be successful and score above 0.5.
                    return (success && score >= configuration.getMinScore());
                })
                .onErrorResumeNext(throwable -> {
                    LOGGER.error("An error occurred when trying to validate ReCaptcha token.", throwable);
                    return Single.just(false);
                });
    }
}

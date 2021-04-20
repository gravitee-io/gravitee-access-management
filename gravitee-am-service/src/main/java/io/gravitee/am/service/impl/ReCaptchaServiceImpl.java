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
package io.gravitee.am.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.service.ReCaptchaService;
import io.reactivex.Single;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.client.WebClient;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ReCaptchaServiceImpl implements ReCaptchaService {

    private static final Logger logger = LoggerFactory.getLogger(ReCaptchaServiceImpl.class);

    @Value("${reCaptcha.enabled:false}")
    private boolean enabled;

    @Value("${reCaptcha.siteKey}")
    private String siteKey;

    @Value("${reCaptcha.secretKey}")
    private String secretKey;

    @Value("${reCaptcha.minScore:0.5}")
    private Double minScore;

    @Value("${reCaptcha.serviceUrl:https://www.google.com/recaptcha/api/siteverify}")
    private String serviceUrl;

    @Autowired
    @Qualifier("recaptchaWebClient")
    private WebClient client;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Single<Boolean> isValid(String token) {
        if (!this.isEnabled()) {
            logger.debug("ReCaptchaService is disabled");
            return Single.just(true);
        }

        logger.debug("ReCaptchaService is enabled");

        if (token == null || "".equals(token.trim())) {
            logger.debug("Recaptcha token is empty");
            return Single.just(false);
        }

        return client
            .post(URI.create(serviceUrl).toString())
            .rxSendForm(MultiMap.caseInsensitiveMultiMap().set("secret", secretKey).set("response", token))
            .map(
                buffer -> {
                    Map res = objectMapper.readValue(buffer.bodyAsString(), Map.class);

                    Boolean success = (Boolean) res.getOrDefault("success", false);
                    Double score = (Double) res.getOrDefault("score", 0.0d);

                    logger.debug("ReCaptchaService success: {} score: {}", success, score);

                    // Result should be successful and score above 0.5.
                    return (success && score >= minScore);
                }
            )
            .onErrorResumeNext(
                throwable -> {
                    logger.error("An error occurred when trying to validate ReCaptcha token.", throwable);
                    return Single.just(false);
                }
            );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSiteKey() {
        return siteKey;
    }
}

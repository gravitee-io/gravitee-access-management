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
package io.gravitee.am.management.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.service.NewsletterService;
import io.reactivex.Single;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class NewsletterServiceImpl implements NewsletterService, InitializingBean, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewsletterServiceImpl.class);
    private ExecutorService executorService;

    @Value("${newsletter.url:https://newsletter.gravitee.io}")
    private String newsletterURI;

    @Value("${newsletter.enabled:true}")
    private boolean newsletterEnabled = true;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    @Qualifier("newsletterWebClient")
    private WebClient client;

    @Override
    public void subscribe(Object user) {
        executorService.execute(() -> {
            client.post(newsletterURI).sendJson(user, handler -> {
                if (handler.failed()) {
                    LOGGER.error("An error has occurred while register newsletter for a user", handler.cause());
                }
            });
        });
    }

    @Override
    public Single<List<String>> getTaglines() {
        if (!newsletterEnabled) {
            return Single.just(Collections.emptyList());
        }

        String taglinesPath = "taglines";
        if (newsletterURI.lastIndexOf('/') != newsletterURI.length() - 1) {
            taglinesPath = "/" + taglinesPath;
        }

        return client
                .getAbs(newsletterURI + taglinesPath)
                .rxSend()
                .map(res -> {
                    if (res.statusCode() != 200) {
                        LOGGER.error("An error has occurred when reading the newsletter taglines response: " + res.statusMessage());
                        return Collections.emptyList();
                    }
                    return mapper.readValue(res.bodyAsString(), List.class);
                });
    }

    @Override
    public void afterPropertiesSet() {
        executorService = Executors.newCachedThreadPool();
    }

    @Override
    public void destroy() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}

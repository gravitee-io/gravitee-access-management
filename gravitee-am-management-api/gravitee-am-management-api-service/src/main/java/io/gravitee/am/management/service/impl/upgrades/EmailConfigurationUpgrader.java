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
package io.gravitee.am.management.service.impl.upgrades;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.scope.ManagementRepositoryScope;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.repository.management.api.ServiceResourceRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.EMAIL_CONFIGURATION_UPGRADER;


@Component
@ManagementRepositoryScope
@Slf4j
public class EmailConfigurationUpgrader extends AsyncUpgrader {

    private static final String SMTP_RESOURCE_NAME = "smtp-am-resource";
    private static final String AUTHENTICATION = "authentication";
    private static final String AUTHENTICATION_TYPE = "authenticationType";
    private static final String DEFAULT_AUTH_TYPE = "basic";


    @Lazy
    @Autowired
    private ServiceResourceRepository serviceResourceRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Completable doUpgrade() {
        log.info("Applying SMTP configuration upgrade (default {}={})", AUTHENTICATION_TYPE, DEFAULT_AUTH_TYPE);

        return Completable.fromPublisher(
                serviceResourceRepository.findByType(SMTP_RESOURCE_NAME)
                        .flatMapSingle(this::upgradeResourceIfNeeded)
        );
    }

    private Single<ServiceResource> upgradeResourceIfNeeded(ServiceResource resource) {
        return Single.fromCallable(() -> applyDefaultAuthTypeIfMissing(resource))
                .flatMap(res -> {
                    if (res == null) {
                        return Single.just(resource);
                    }
                    return serviceResourceRepository.update(res)
                            .doOnSuccess(updated ->
                                    log.info("SMTP configuration upgrade applied for resource: {}", updated.getId())
                            );
                })
                .onErrorResumeNext(err -> {
                    log.warn("SMTP configuration upgrade skipped for resource {} due to error: {}",
                            resource.getId(), err.toString());
                    return Single.just(resource);
                });
    }

    private ServiceResource applyDefaultAuthTypeIfMissing(ServiceResource resource) throws Exception {
        final String json = resource.getConfiguration();
        if (json == null || json.isBlank()) {
            return null;
        }

        final Map<String, Object> cfg = mapper.readValue(json, new TypeReference<>() {});

        final boolean authenticationEnabled = Boolean.TRUE.equals(cfg.get(AUTHENTICATION));
        final Object authenticationType = cfg.get(AUTHENTICATION_TYPE);

        if (authenticationEnabled && authenticationType == null) {
            cfg.put(AUTHENTICATION_TYPE, DEFAULT_AUTH_TYPE);
            resource.setConfiguration(mapper.writeValueAsString(cfg));
            return resource;
        }

        return null;
    }

    @Override
    public int getOrder() {
        return EMAIL_CONFIGURATION_UPGRADER;
    }
}

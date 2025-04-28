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
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.AuditReporterService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AuditServiceImpl implements AuditService, InitializingBean, DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditServiceImpl.class);
    private static final String AUDIT_TO_EXCLUDE_KEY = "reporters.audits.excluded_audit_types[%d]";

    @Autowired
    private AuditReporterService auditReporterService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    private Set<String> auditsToExclude = Collections.synchronizedSet(new HashSet<>());

    private ExecutorService executorService;

    @Override
    public void report(AuditBuilder<?> auditBuilder) {
        executorService.execute(() -> {
            final var audit = auditBuilder.build(objectMapper);
            if (!auditsToExclude.contains(audit.getType())) {
                auditReporterService.report(audit);
            }
        });
    }

    @Override
    public void afterPropertiesSet() {
        executorService = Executors.newCachedThreadPool();
        int i = 0;
        do {
            final var auditType = environment.getProperty(AUDIT_TO_EXCLUDE_KEY.formatted(i), String.class);
            if (auditType != null) {
                LOGGER.debug("Audit type '{}' will be excluded", auditType);
                auditsToExclude.add(auditType);
            }
        }
        while(environment.containsProperty(AUDIT_TO_EXCLUDE_KEY.formatted(++i)));
    }

    @Override
    public void destroy() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }
}

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
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.AuditReporterService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AuditServiceImpl implements AuditService, InitializingBean, DisposableBean {

    @Autowired
    private AuditReporterService auditReporterService;

    @Autowired
    private ObjectMapper objectMapper;

    private ExecutorService executorService;

    @Override
    public void report(AuditBuilder auditBuilder) {
        executorService.execute(() -> auditReporterService.report(auditBuilder.build(objectMapper)));
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

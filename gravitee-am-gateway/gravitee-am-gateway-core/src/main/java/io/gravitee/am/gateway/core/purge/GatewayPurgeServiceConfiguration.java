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
package io.gravitee.am.gateway.core.purge;

import io.gravitee.am.repository.common.ExpiredDataSweeper.Target;
import io.gravitee.am.repository.common.ExpiredDataSweeperProvider;
import io.gravitee.am.service.purge.ScheduledPurgeService;
import io.gravitee.am.service.purge.ScheduledPurgeServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;

import static io.gravitee.am.repository.common.ExpiredDataSweeper.Target.*;

@Configuration
@Slf4j
public class GatewayPurgeServiceConfiguration {
    private static final List<Target> GATEWAY_PURGE_TARGETS = List.of(
            access_tokens,
            authorization_codes,
            refresh_tokens,
            scope_approvals,
            request_objects,
            login_attempts,
            uma_permission_ticket,
            auth_flow_ctx,
            pushed_authorization_requests,
            ciba_auth_requests,
            user_activities,
            devices);

    private final ScheduledPurgeServiceFactory factory = new ScheduledPurgeServiceFactory(GATEWAY_PURGE_TARGETS);

    @Bean
    public ScheduledPurgeService scheduledPurgeService(@Value("${services.purge.enabled:true}") boolean enabled,
                                                       @Value("${repositories.gateway.type:mongodb}") String gatewayRepositoryType,
                                                       @Value("${repositories.oauth2.type:mongodb}") String oAuth2repositoryType,
                                                       @Value("${services.purge.cron:0 0 23 * * *}") String cron,
                                                       @Value("#{'${services.purge.exclude:}'.empty ? {} : '${services.purge.exclude}'.split(',')}") List<String> excluded,
                                                       TaskScheduler taskScheduler,
                                                       ExpiredDataSweeperProvider sweepers) {
        enabled = enabled && ("jdbc".equalsIgnoreCase(gatewayRepositoryType) || "jdbc".equalsIgnoreCase(oAuth2repositoryType));
        return factory.createPurgeService(enabled, cron, excluded, taskScheduler, sweepers);
    }

    @Bean
    public GatewayExpiredDataSweeperProvider expiredDataSweeperProvider() {
        return new GatewayExpiredDataSweeperProvider();
    }

}

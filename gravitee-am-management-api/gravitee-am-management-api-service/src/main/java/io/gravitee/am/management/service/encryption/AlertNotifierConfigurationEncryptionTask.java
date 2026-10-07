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
package io.gravitee.am.management.service.encryption;

import io.gravitee.am.common.scope.ManagementRepositoryScope;
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.repository.management.api.AlertNotifierRepository;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author GraviteeSource Team
 */
@Component
@ManagementRepositoryScope
public class AlertNotifierConfigurationEncryptionTask extends ConfigurationEncryptionTask<AlertNotifier> {

    public AlertNotifierConfigurationEncryptionTask(@Lazy SystemTaskRepository systemTaskRepository,
                                                    @Lazy AlertNotifierRepository repository) {
        super(systemTaskRepository, "alert_notifier", repository, repository::findAll);
    }
}

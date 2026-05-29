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
package io.gravitee.am.service.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Marker subclass that flags a reporter as created by the Automation API so the service layer can set
 * {@code managedBy = AUTOMATION_API}, carrying the external {@code key} for the resource. The deterministic
 * {@code id} is carried by {@link NewReporter#getId()}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class AutomationNewReporter extends NewReporter {

    private String automationKey;
}

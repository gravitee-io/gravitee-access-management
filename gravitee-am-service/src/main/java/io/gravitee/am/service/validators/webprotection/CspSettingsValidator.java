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
package io.gravitee.am.service.validators.webprotection;

import io.gravitee.am.model.webprotection.CspSettings;
import io.gravitee.am.service.validators.Validator;
import io.reactivex.rxjava3.core.Completable;

/**
 * Validates domain-level {@link CspSettings} so that an invalid policy cannot be persisted and then
 * fail at request time on the gateway.
 *
 * @author GraviteeSource Team
 */
public interface CspSettingsValidator extends Validator<CspSettings, Completable> {
}

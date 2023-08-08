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
package io.gravitee.am.service.validators.resource;

import io.gravitee.am.service.validators.Validator;
import io.gravitee.am.service.validators.resource.ResourceValidator.ResourceHolder;
import io.reactivex.rxjava3.core.Completable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ResourceValidator extends Validator<ResourceHolder, Completable> {

    class ResourceHolder {
        private final String name;
        private final String configuration;

        public ResourceHolder(String name, String configuration) {
            this.name = name;
            this.configuration = configuration;
        }

        public String getName() {
            return name;
        }

        public String getConfiguration() {
            return configuration;
        }
    }
}

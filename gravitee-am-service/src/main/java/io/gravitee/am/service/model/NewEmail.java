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

import io.gravitee.am.model.Template;

<<<<<<< HEAD
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
=======
import jakarta.validation.constraints.NotNull;
>>>>>>> 8c006cf9c1 (feat: email allow list to protect from impersonation)

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class NewEmail extends AbstractEmail {
    @NotNull
    private Template template;

    public Template getTemplate() {
        return template;
    }

    public NewEmail setTemplate(Template template) {
        this.template = template;
        return this;
    }
}

/*
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
import type { Form as FormModel } from '@am-models/Form';
import { http } from './http';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';

/** `id` is absent while the domain still uses the default template. */
export type Form = ApiModel<FormModel, 'template' | 'enabled' | 'content'>;

export function getForm(ref: DomainRef, template: string): Promise<Form> {
    return http<Form>(`${domainPath(ref)}/forms?template=${template}`);
}

/** Creates the domain's own form the first time, then updates it. */
export function saveForm(ref: DomainRef, form: Form): Promise<Form> {
    const forms = `${domainPath(ref)}/forms`;
    return form.id
        ? http<Form>(`${forms}/${form.id}`, { method: 'PUT', body: JSON.stringify({ enabled: form.enabled, content: form.content }) })
        : http<Form>(forms, {
              method: 'POST',
              body: JSON.stringify({ template: form.template, enabled: form.enabled, content: form.content }),
          });
}

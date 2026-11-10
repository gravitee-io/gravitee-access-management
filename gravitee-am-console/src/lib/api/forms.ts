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

/** A custom page of the organization. The Management API answers with an empty object when the template has none. */
export type OrganizationForm = ApiModel<FormModel>;

export interface OrganizationFormInput {
    readonly template: string;
    readonly enabled: boolean;
    readonly content: string;
}

export function getOrganizationForm(organizationId: string, template: string): Promise<OrganizationForm> {
    return http<OrganizationForm>(`/organizations/${organizationId}/forms?template=${encodeURIComponent(template)}`);
}

export function createOrganizationForm(organizationId: string, form: OrganizationFormInput): Promise<OrganizationForm> {
    return http<OrganizationForm>(`/organizations/${organizationId}/forms`, { method: 'POST', body: JSON.stringify(form) });
}

export function updateOrganizationForm(organizationId: string, formId: string, form: OrganizationFormInput): Promise<OrganizationForm> {
    return http<OrganizationForm>(`/organizations/${organizationId}/forms/${encodeURIComponent(formId)}`, {
        method: 'PUT',
        body: JSON.stringify({ enabled: form.enabled, content: form.content }),
    });
}

export function deleteOrganizationForm(organizationId: string, formId: string): Promise<void> {
    return http<void>(`/organizations/${organizationId}/forms/${encodeURIComponent(formId)}`, { method: 'DELETE' });
}

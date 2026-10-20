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
import type { Application as ApplicationModel } from '@am-models/Application';
import type { CimdValidationResponse } from '@am-models/CimdValidationResponse';
import type { FilteredApplication } from '@am-models/FilteredApplication';
import type { NewApplication as NewApplicationModel } from '@am-models/NewApplication';
import type { NewCimdApplication as NewCimdApplicationModel } from '@am-models/NewCimdApplication';
import type { PatchApplication } from '@am-models/PatchApplication';
import { http } from './http';
import type { Page } from './management-api';
import type { ApiModel } from './model';
import { type DomainRef, domainPath, searchParam } from './paths';

/** The Management API returns the type in lowercase. The spec lists it in uppercase. */
export type ApplicationType = 'web' | 'native' | 'browser' | 'service' | 'resource_server';

export type Application = ApiModel<Omit<FilteredApplication, 'type'> & { type: ApplicationType }, 'id' | 'name' | 'enabled'>;

export type ApplicationDetail = ApiModel<Omit<ApplicationModel, 'type'> & { type: ApplicationType }, 'id' | 'name' | 'enabled'>;

export function listApplications(ref: DomainRef, page: number, size: number, query = ''): Promise<Page<Application>> {
    return http<Page<Application>>(`${domainPath(ref)}/applications?page=${page}&size=${size}${searchParam(query)}`);
}

export function getApplication(ref: DomainRef, applicationId: string): Promise<ApplicationDetail> {
    return http<ApplicationDetail>(`${domainPath(ref)}/applications/${applicationId}`);
}

export function patchApplication(ref: DomainRef, applicationId: string, patch: PatchApplication): Promise<ApplicationDetail> {
    return http<ApplicationDetail>(`${domainPath(ref)}/applications/${applicationId}`, {
        method: 'PATCH',
        body: JSON.stringify(patch),
    });
}

/** The type names the create endpoints take. */
export type NewApplicationType = Exclude<NewApplicationModel['type'], 'AGENT'>;

/** The Angular console sends `redirectUris: null` when the type takes none. */
export type NewApplication = ApiModel<
    Omit<NewApplicationModel, 'type' | 'redirectUris'> & { type: NewApplicationType; redirectUris: string[] | null }
>;

export function createApplication(ref: DomainRef, application: NewApplication): Promise<ApplicationDetail> {
    return http<ApplicationDetail>(`${domainPath(ref)}/applications`, { method: 'POST', body: JSON.stringify(application) });
}

/** OAuth 2.0 client metadata, as the Client Identity Metadata Document holds it. */
export interface ClientMetadata {
    readonly client_name?: string;
    readonly redirect_uris?: string[];
    readonly grant_types?: string[];
    readonly response_types?: string[];
    readonly scope?: string;
    readonly token_endpoint_auth_method?: string;
    readonly logo_uri?: string;
    readonly jwks_uri?: string;
    readonly jwks?: { readonly keys?: { readonly kid?: string; readonly kty?: string; readonly use?: string; readonly alg?: string }[] };
    readonly application_type?: string;
    readonly post_logout_redirect_uris?: string[];
    readonly contacts?: string[];
    readonly client_uri?: string;
    readonly policy_uri?: string;
    readonly tos_uri?: string;
    readonly request_uris?: string[];
    readonly software_id?: string;
    readonly software_version?: string;
    readonly software_statement?: string;
    readonly subject_type?: string;
    readonly sector_identifier_uri?: string;
    readonly id_token_signed_response_alg?: string;
    readonly tls_client_auth_subject_dn?: string;
    readonly tls_client_auth_san_dns?: string;
    readonly tls_client_auth_san_uri?: string;
    readonly tls_client_auth_san_ip?: string;
    readonly tls_client_auth_san_email?: string;
    readonly tls_client_certificate_bound_access_tokens?: boolean;
    readonly backchannel_token_delivery_mode?: string;
    readonly backchannel_authentication_request_signing_alg?: string;
    readonly backchannel_client_notification_endpoint?: string;
    readonly backchannel_user_code_parameter?: boolean;
}

export type CimdPreview = ApiModel<Omit<CimdValidationResponse, 'metadata'> & { metadata?: ClientMetadata }, 'url'>;

export function validateCimd(ref: DomainRef, url: string): Promise<CimdPreview> {
    return http<CimdPreview>(`${domainPath(ref)}/cimd/validate`, { method: 'POST', body: JSON.stringify({ url }) });
}

export type NewCimdApplication = ApiModel<Omit<NewCimdApplicationModel, 'type'> & { type: NewApplicationType }>;

export function createApplicationFromCimd(ref: DomainRef, application: NewCimdApplication): Promise<ApplicationDetail> {
    return http<ApplicationDetail>(`${domainPath(ref)}/cimd/applications`, { method: 'POST', body: JSON.stringify(application) });
}

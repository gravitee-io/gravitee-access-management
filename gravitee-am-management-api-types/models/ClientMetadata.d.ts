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
/**
 * OAuth 2.0 client metadata as defined in the IANA OAuth Parameters registry (https://www.iana.org/assignments/oauth-parameters/oauth-parameters.xhtml#client-metadata).
 * @export
 * @interface ClientMetadata
 */
export interface ClientMetadata {
    [key: string]: any | any;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    application_type?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    backchannel_authentication_request_signing_alg?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    backchannel_client_notification_endpoint?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    backchannel_token_delivery_mode?: string;
    /**
     *
     * @type {boolean}
     * @memberof ClientMetadata
     */
    backchannel_user_code_parameter?: boolean;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    client_id?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    client_name?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    client_uri?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof ClientMetadata
     */
    contacts?: Array<string>;
    /**
     *
     * @type {Array<string>}
     * @memberof ClientMetadata
     */
    grant_types?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    id_token_signed_response_alg?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    jwks_uri?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    logo_uri?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    policy_uri?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof ClientMetadata
     */
    post_logout_redirect_uris?: Array<string>;
    /**
     *
     * @type {Array<string>}
     * @memberof ClientMetadata
     */
    redirect_uris?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    request_object_signing_alg?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof ClientMetadata
     */
    request_uris?: Array<string>;
    /**
     *
     * @type {Array<string>}
     * @memberof ClientMetadata
     */
    response_types?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    scope?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    sector_identifier_uri?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    software_id?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    software_statement?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    software_version?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    subject_type?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    tls_client_auth_san_dns?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    tls_client_auth_san_email?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    tls_client_auth_san_ip?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    tls_client_auth_san_uri?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    tls_client_auth_subject_dn?: string;
    /**
     *
     * @type {boolean}
     * @memberof ClientMetadata
     */
    tls_client_certificate_bound_access_tokens?: boolean;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    token_endpoint_auth_method?: string;
    /**
     *
     * @type {string}
     * @memberof ClientMetadata
     */
    tos_uri?: string;
}
/**
 * Check if a given object implements the ClientMetadata interface.
 */
export declare function instanceOfClientMetadata(value: object): value is ClientMetadata;
export declare function ClientMetadataFromJSON(json: any): ClientMetadata;
export declare function ClientMetadataFromJSONTyped(json: any, ignoreDiscriminator: boolean): ClientMetadata;
export declare function ClientMetadataToJSON(json: any): ClientMetadata;
export declare function ClientMetadataToJSONTyped(value?: ClientMetadata | null, ignoreDiscriminator?: boolean): any;

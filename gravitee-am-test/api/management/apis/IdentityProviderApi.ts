/* tslint:disable */
/* eslint-disable */
/**
 * Gravitee.io - Access Management API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


import * as runtime from '../runtime';
import {
    IdentityProvider,
    IdentityProviderFromJSON,
    IdentityProviderToJSON,
    NewIdentityProvider,
    NewIdentityProviderFromJSON,
    NewIdentityProviderToJSON,
    UpdateIdentityProvider,
    UpdateIdentityProviderFromJSON,
    UpdateIdentityProviderToJSON,
} from '../models';

export interface Create15Request {
    organizationId: string;
    identity: NewIdentityProvider;
}

export interface CreateIdentityProviderRequest {
    organizationId: string;
    environmentId: string;
    domain: string;
    identity: NewIdentityProvider;
}

export interface Delete18Request {
    organizationId: string;
    identity: string;
}

export interface DeleteIdentityProviderRequest {
    organizationId: string;
    environmentId: string;
    domain: string;
    identity: string;
}

export interface FindIdentityProviderRequest {
    organizationId: string;
    environmentId: string;
    domain: string;
    identity: string;
}

export interface Get27Request {
    organizationId: string;
    identity: string;
}

export interface Get33Request {
    identity: string;
}

export interface GetSchema2Request {
    identity: string;
}

export interface List28Request {
    organizationId: string;
    userProvider?: boolean;
}

export interface List33Request {
    external?: boolean;
    expand?: Array<string>;
}

export interface ListIdentityProvidersRequest {
    organizationId: string;
    environmentId: string;
    domain: string;
    userProvider?: boolean;
}

export interface Update18Request {
    organizationId: string;
    identity: string;
    identity2: UpdateIdentityProvider;
}

export interface UpdateIdentityProviderRequest {
    organizationId: string;
    environmentId: string;
    domain: string;
    identity: string;
    identity2: UpdateIdentityProvider;
}

/**
 * 
 */
export class IdentityProviderApi extends runtime.BaseAPI {

    /**
     * User must have the ORGANIZATION_IDENTITY_PROVIDER[CREATE] permission on the specified organization
     * Create an identity provider for the organization
     */
    async create15Raw(requestParameters: Create15Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling create15.');
        }

        if (requestParameters.identity === null || requestParameters.identity === undefined) {
            throw new runtime.RequiredError('identity','Required parameter requestParameters.identity was null or undefined when calling create15.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/identities`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))),
            method: 'POST',
            headers: headerParameters,
            query: queryParameters,
            body: NewIdentityProviderToJSON(requestParameters.identity),
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have the ORGANIZATION_IDENTITY_PROVIDER[CREATE] permission on the specified organization
     * Create an identity provider for the organization
     */
    async create15(requestParameters: Create15Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.create15Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the DOMAIN_IDENTITY_PROVIDER[CREATE] permission on the specified domain or DOMAIN_IDENTITY_PROVIDER[CREATE] permission on the specified environment or DOMAIN_IDENTITY_PROVIDER[CREATE] permission on the specified organization
     * Create an identity provider
     */
    async createIdentityProviderRaw(requestParameters: CreateIdentityProviderRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<IdentityProvider>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling createIdentityProvider.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling createIdentityProvider.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling createIdentityProvider.');
        }

        if (requestParameters.identity === null || requestParameters.identity === undefined) {
            throw new runtime.RequiredError('identity','Required parameter requestParameters.identity was null or undefined when calling createIdentityProvider.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/identities`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))),
            method: 'POST',
            headers: headerParameters,
            query: queryParameters,
            body: NewIdentityProviderToJSON(requestParameters.identity),
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => IdentityProviderFromJSON(jsonValue));
    }

    /**
     * User must have the DOMAIN_IDENTITY_PROVIDER[CREATE] permission on the specified domain or DOMAIN_IDENTITY_PROVIDER[CREATE] permission on the specified environment or DOMAIN_IDENTITY_PROVIDER[CREATE] permission on the specified organization
     * Create an identity provider
     */
    async createIdentityProvider(requestParameters: CreateIdentityProviderRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<IdentityProvider> {
        const response = await this.createIdentityProviderRaw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * User must have the ORGANIZATION_IDENTITY_PROVIDER[DELETE] permission on the specified organization
     * Delete an identity provider
     */
    async delete18Raw(requestParameters: Delete18Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling delete18.');
        }

        if (requestParameters.identity === null || requestParameters.identity === undefined) {
            throw new runtime.RequiredError('identity','Required parameter requestParameters.identity was null or undefined when calling delete18.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/identities/{identity}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"identity"}}`, encodeURIComponent(String(requestParameters.identity))),
            method: 'DELETE',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have the ORGANIZATION_IDENTITY_PROVIDER[DELETE] permission on the specified organization
     * Delete an identity provider
     */
    async delete18(requestParameters: Delete18Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.delete18Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the DOMAIN_IDENTITY_PROVIDER[DELETE] permission on the specified domain or DOMAIN_IDENTITY_PROVIDER[DELETE] permission on the specified environment or DOMAIN_IDENTITY_PROVIDER[DELETE] permission on the specified organization
     * Delete an identity provider
     */
    async deleteIdentityProviderRaw(requestParameters: DeleteIdentityProviderRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling deleteIdentityProvider.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling deleteIdentityProvider.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling deleteIdentityProvider.');
        }

        if (requestParameters.identity === null || requestParameters.identity === undefined) {
            throw new runtime.RequiredError('identity','Required parameter requestParameters.identity was null or undefined when calling deleteIdentityProvider.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/identities/{identity}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"identity"}}`, encodeURIComponent(String(requestParameters.identity))),
            method: 'DELETE',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have the DOMAIN_IDENTITY_PROVIDER[DELETE] permission on the specified domain or DOMAIN_IDENTITY_PROVIDER[DELETE] permission on the specified environment or DOMAIN_IDENTITY_PROVIDER[DELETE] permission on the specified organization
     * Delete an identity provider
     */
    async deleteIdentityProvider(requestParameters: DeleteIdentityProviderRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.deleteIdentityProviderRaw(requestParameters, initOverrides);
    }

    /**
     * User must have the DOMAIN_IDENTITY_PROVIDER[READ] permission on the specified domain or DOMAIN_IDENTITY_PROVIDER[READ] permission on the specified environment or DOMAIN_IDENTITY_PROVIDER[READ] permission on the specified organization
     * Get an identity provider
     */
    async findIdentityProviderRaw(requestParameters: FindIdentityProviderRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<IdentityProvider>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling findIdentityProvider.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling findIdentityProvider.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling findIdentityProvider.');
        }

        if (requestParameters.identity === null || requestParameters.identity === undefined) {
            throw new runtime.RequiredError('identity','Required parameter requestParameters.identity was null or undefined when calling findIdentityProvider.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/identities/{identity}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"identity"}}`, encodeURIComponent(String(requestParameters.identity))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => IdentityProviderFromJSON(jsonValue));
    }

    /**
     * User must have the DOMAIN_IDENTITY_PROVIDER[READ] permission on the specified domain or DOMAIN_IDENTITY_PROVIDER[READ] permission on the specified environment or DOMAIN_IDENTITY_PROVIDER[READ] permission on the specified organization
     * Get an identity provider
     */
    async findIdentityProvider(requestParameters: FindIdentityProviderRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<IdentityProvider> {
        const response = await this.findIdentityProviderRaw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * User must have the ORGANIZATION_IDENTITY_PROVIDER[READ] permission on the specified organization
     * Get an identity provider
     */
    async get27Raw(requestParameters: Get27Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<IdentityProvider>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling get27.');
        }

        if (requestParameters.identity === null || requestParameters.identity === undefined) {
            throw new runtime.RequiredError('identity','Required parameter requestParameters.identity was null or undefined when calling get27.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/identities/{identity}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"identity"}}`, encodeURIComponent(String(requestParameters.identity))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => IdentityProviderFromJSON(jsonValue));
    }

    /**
     * User must have the ORGANIZATION_IDENTITY_PROVIDER[READ] permission on the specified organization
     * Get an identity provider
     */
    async get27(requestParameters: Get27Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<IdentityProvider> {
        const response = await this.get27Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * Get an identity provider
     */
    async get33Raw(requestParameters: Get33Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.identity === null || requestParameters.identity === undefined) {
            throw new runtime.RequiredError('identity','Required parameter requestParameters.identity was null or undefined when calling get33.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/platform/plugins/identities/{identity}`.replace(`{${"identity"}}`, encodeURIComponent(String(requestParameters.identity))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * Get an identity provider
     */
    async get33(requestParameters: Get33Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.get33Raw(requestParameters, initOverrides);
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * Get an identity provider plugin\'s schema
     */
    async getSchema2Raw(requestParameters: GetSchema2Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.identity === null || requestParameters.identity === undefined) {
            throw new runtime.RequiredError('identity','Required parameter requestParameters.identity was null or undefined when calling getSchema2.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/platform/plugins/identities/{identity}/schema`.replace(`{${"identity"}}`, encodeURIComponent(String(requestParameters.identity))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * Get an identity provider plugin\'s schema
     */
    async getSchema2(requestParameters: GetSchema2Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.getSchema2Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the ORGANIZATION_IDENTITY_PROVIDER[LIST] permission on the specified organization. Each returned identity provider is filtered and contains only basic information such as id, name, type and isExternal.
     * List registered identity providers of the organization
     */
    async list28Raw(requestParameters: List28Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Set<IdentityProvider>>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling list28.');
        }

        const queryParameters: any = {};

        if (requestParameters.userProvider !== undefined) {
            queryParameters['userProvider'] = requestParameters.userProvider;
        }

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/identities`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => new Set(jsonValue.map(IdentityProviderFromJSON)));
    }

    /**
     * User must have the ORGANIZATION_IDENTITY_PROVIDER[LIST] permission on the specified organization. Each returned identity provider is filtered and contains only basic information such as id, name, type and isExternal.
     * List registered identity providers of the organization
     */
    async list28(requestParameters: List28Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Set<IdentityProvider>> {
        const response = await this.list28Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * List identity provider plugins
     */
    async list33Raw(requestParameters: List33Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        const queryParameters: any = {};

        if (requestParameters.external !== undefined) {
            queryParameters['external'] = requestParameters.external;
        }

        if (requestParameters.expand) {
            queryParameters['expand'] = requestParameters.expand;
        }

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/platform/plugins/identities`,
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * List identity provider plugins
     */
    async list33(requestParameters: List33Request = {}, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.list33Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the DOMAIN_IDENTITY_PROVIDER[LIST] permission on the specified domain or DOMAIN_IDENTITY_PROVIDER[LIST] permission on the specified environment or DOMAIN_IDENTITY_PROVIDER[LIST] permission on the specified organization. Each returned identity provider is filtered and contains only basic information such as id, name and type.
     * List registered identity providers for a security domain
     */
    async listIdentityProvidersRaw(requestParameters: ListIdentityProvidersRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Set<IdentityProvider>>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling listIdentityProviders.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling listIdentityProviders.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling listIdentityProviders.');
        }

        const queryParameters: any = {};

        if (requestParameters.userProvider !== undefined) {
            queryParameters['userProvider'] = requestParameters.userProvider;
        }

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/identities`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => new Set(jsonValue.map(IdentityProviderFromJSON)));
    }

    /**
     * User must have the DOMAIN_IDENTITY_PROVIDER[LIST] permission on the specified domain or DOMAIN_IDENTITY_PROVIDER[LIST] permission on the specified environment or DOMAIN_IDENTITY_PROVIDER[LIST] permission on the specified organization. Each returned identity provider is filtered and contains only basic information such as id, name and type.
     * List registered identity providers for a security domain
     */
    async listIdentityProviders(requestParameters: ListIdentityProvidersRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Set<IdentityProvider>> {
        const response = await this.listIdentityProvidersRaw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * User must have the ORGANIZATION_IDENTITY_PROVIDER[UPDATE] permission on the specified organization
     * Update an identity provider
     */
    async update18Raw(requestParameters: Update18Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<IdentityProvider>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling update18.');
        }

        if (requestParameters.identity === null || requestParameters.identity === undefined) {
            throw new runtime.RequiredError('identity','Required parameter requestParameters.identity was null or undefined when calling update18.');
        }

        if (requestParameters.identity2 === null || requestParameters.identity2 === undefined) {
            throw new runtime.RequiredError('identity2','Required parameter requestParameters.identity2 was null or undefined when calling update18.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/identities/{identity}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"identity"}}`, encodeURIComponent(String(requestParameters.identity))),
            method: 'PUT',
            headers: headerParameters,
            query: queryParameters,
            body: UpdateIdentityProviderToJSON(requestParameters.identity2),
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => IdentityProviderFromJSON(jsonValue));
    }

    /**
     * User must have the ORGANIZATION_IDENTITY_PROVIDER[UPDATE] permission on the specified organization
     * Update an identity provider
     */
    async update18(requestParameters: Update18Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<IdentityProvider> {
        const response = await this.update18Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * User must have the DOMAIN_IDENTITY_PROVIDER[UPDATE] permission on the specified domain or DOMAIN_IDENTITY_PROVIDER[UPDATE] permission on the specified environment or DOMAIN_IDENTITY_PROVIDER[UPDATE] permission on the specified organization
     * Update an identity provider
     */
    async updateIdentityProviderRaw(requestParameters: UpdateIdentityProviderRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<IdentityProvider>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling updateIdentityProvider.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling updateIdentityProvider.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling updateIdentityProvider.');
        }

        if (requestParameters.identity === null || requestParameters.identity === undefined) {
            throw new runtime.RequiredError('identity','Required parameter requestParameters.identity was null or undefined when calling updateIdentityProvider.');
        }

        if (requestParameters.identity2 === null || requestParameters.identity2 === undefined) {
            throw new runtime.RequiredError('identity2','Required parameter requestParameters.identity2 was null or undefined when calling updateIdentityProvider.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/identities/{identity}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"identity"}}`, encodeURIComponent(String(requestParameters.identity))),
            method: 'PUT',
            headers: headerParameters,
            query: queryParameters,
            body: UpdateIdentityProviderToJSON(requestParameters.identity2),
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => IdentityProviderFromJSON(jsonValue));
    }

    /**
     * User must have the DOMAIN_IDENTITY_PROVIDER[UPDATE] permission on the specified domain or DOMAIN_IDENTITY_PROVIDER[UPDATE] permission on the specified environment or DOMAIN_IDENTITY_PROVIDER[UPDATE] permission on the specified organization
     * Update an identity provider
     */
    async updateIdentityProvider(requestParameters: UpdateIdentityProviderRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<IdentityProvider> {
        const response = await this.updateIdentityProviderRaw(requestParameters, initOverrides);
        return await response.value();
    }

}

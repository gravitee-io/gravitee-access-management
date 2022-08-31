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
    Entrypoint,
    EntrypointFromJSON,
    EntrypointToJSON,
    NewEntrypoint,
    NewEntrypointFromJSON,
    NewEntrypointToJSON,
    UpdateEntrypoint,
    UpdateEntrypointFromJSON,
    UpdateEntrypointToJSON,
} from '../models';

export interface Create12Request {
    organizationId: string;
    entrypoint: NewEntrypoint;
}

export interface Delete15Request {
    organizationId: string;
    entrypointId: string;
}

export interface Get24Request {
    organizationId: string;
    entrypointId: string;
}

export interface List24Request {
    organizationId: string;
}

export interface Update16Request {
    organizationId: string;
    entrypointId: string;
    entrypoint: UpdateEntrypoint;
}

/**
 * 
 */
export class EntrypointsApi extends runtime.BaseAPI {

    /**
     * User must have the ORGANIZATION_ENTRYPOINT[CREATE] permission on the specified organization
     * Create a entrypoint
     */
    async create12Raw(requestParameters: Create12Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling create12.');
        }

        if (requestParameters.entrypoint === null || requestParameters.entrypoint === undefined) {
            throw new runtime.RequiredError('entrypoint','Required parameter requestParameters.entrypoint was null or undefined when calling create12.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/entrypoints`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))),
            method: 'POST',
            headers: headerParameters,
            query: queryParameters,
            body: NewEntrypointToJSON(requestParameters.entrypoint),
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have the ORGANIZATION_ENTRYPOINT[CREATE] permission on the specified organization
     * Create a entrypoint
     */
    async create12(requestParameters: Create12Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.create12Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the ORGANIZATION_ENTRYPOINT[DELETE] permission on the specified organization
     * Delete the sharding entrypoint
     */
    async delete15Raw(requestParameters: Delete15Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling delete15.');
        }

        if (requestParameters.entrypointId === null || requestParameters.entrypointId === undefined) {
            throw new runtime.RequiredError('entrypointId','Required parameter requestParameters.entrypointId was null or undefined when calling delete15.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/entrypoints/{entrypointId}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"entrypointId"}}`, encodeURIComponent(String(requestParameters.entrypointId))),
            method: 'DELETE',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have the ORGANIZATION_ENTRYPOINT[DELETE] permission on the specified organization
     * Delete the sharding entrypoint
     */
    async delete15(requestParameters: Delete15Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.delete15Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the ORGANIZATION_ENTRYPOINT[READ] permission on the specified organization
     * Get a sharding entrypoint
     */
    async get24Raw(requestParameters: Get24Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Entrypoint>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling get24.');
        }

        if (requestParameters.entrypointId === null || requestParameters.entrypointId === undefined) {
            throw new runtime.RequiredError('entrypointId','Required parameter requestParameters.entrypointId was null or undefined when calling get24.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/entrypoints/{entrypointId}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"entrypointId"}}`, encodeURIComponent(String(requestParameters.entrypointId))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => EntrypointFromJSON(jsonValue));
    }

    /**
     * User must have the ORGANIZATION_ENTRYPOINT[READ] permission on the specified organization
     * Get a sharding entrypoint
     */
    async get24(requestParameters: Get24Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Entrypoint> {
        const response = await this.get24Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * User must have the ORGANIZATION[LIST] permission on the specified organization. Each returned entrypoint is filtered and contains only basic information such as id and name.
     * List entrypoints
     */
    async list24Raw(requestParameters: List24Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Array<Entrypoint>>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling list24.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/entrypoints`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => jsonValue.map(EntrypointFromJSON));
    }

    /**
     * User must have the ORGANIZATION[LIST] permission on the specified organization. Each returned entrypoint is filtered and contains only basic information such as id and name.
     * List entrypoints
     */
    async list24(requestParameters: List24Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Array<Entrypoint>> {
        const response = await this.list24Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * User must have the ORGANIZATION_ENTRYPOINT[UPDATE] permission on the specified organization
     * Update the sharding entrypoint
     */
    async update16Raw(requestParameters: Update16Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Entrypoint>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling update16.');
        }

        if (requestParameters.entrypointId === null || requestParameters.entrypointId === undefined) {
            throw new runtime.RequiredError('entrypointId','Required parameter requestParameters.entrypointId was null or undefined when calling update16.');
        }

        if (requestParameters.entrypoint === null || requestParameters.entrypoint === undefined) {
            throw new runtime.RequiredError('entrypoint','Required parameter requestParameters.entrypoint was null or undefined when calling update16.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/entrypoints/{entrypointId}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"entrypointId"}}`, encodeURIComponent(String(requestParameters.entrypointId))),
            method: 'PUT',
            headers: headerParameters,
            query: queryParameters,
            body: UpdateEntrypointToJSON(requestParameters.entrypoint),
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => EntrypointFromJSON(jsonValue));
    }

    /**
     * User must have the ORGANIZATION_ENTRYPOINT[UPDATE] permission on the specified organization
     * Update the sharding entrypoint
     */
    async update16(requestParameters: Update16Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Entrypoint> {
        const response = await this.update16Raw(requestParameters, initOverrides);
        return await response.value();
    }

}

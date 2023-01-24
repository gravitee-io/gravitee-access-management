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

export interface Create11Request {
    organizationId: string;
    entrypoint: NewEntrypoint;
}

export interface Delete14Request {
    organizationId: string;
    entrypointId: string;
}

export interface Get21Request {
    organizationId: string;
    entrypointId: string;
}

export interface List21Request {
    organizationId: string;
}

export interface Update11Request {
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
    async create11Raw(requestParameters: Create11Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling create11.');
        }

        if (requestParameters.entrypoint === null || requestParameters.entrypoint === undefined) {
            throw new runtime.RequiredError('entrypoint','Required parameter requestParameters.entrypoint was null or undefined when calling create11.');
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
    async create11(requestParameters: Create11Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.create11Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the ORGANIZATION_ENTRYPOINT[DELETE] permission on the specified organization
     * Delete the sharding entrypoint
     */
    async delete14Raw(requestParameters: Delete14Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling delete14.');
        }

        if (requestParameters.entrypointId === null || requestParameters.entrypointId === undefined) {
            throw new runtime.RequiredError('entrypointId','Required parameter requestParameters.entrypointId was null or undefined when calling delete14.');
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
    async delete14(requestParameters: Delete14Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.delete14Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the ORGANIZATION_ENTRYPOINT[READ] permission on the specified organization
     * Get a sharding entrypoint
     */
    async get21Raw(requestParameters: Get21Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Entrypoint>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling get21.');
        }

        if (requestParameters.entrypointId === null || requestParameters.entrypointId === undefined) {
            throw new runtime.RequiredError('entrypointId','Required parameter requestParameters.entrypointId was null or undefined when calling get21.');
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
    async get21(requestParameters: Get21Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Entrypoint> {
        const response = await this.get21Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * User must have the ORGANIZATION[LIST] permission on the specified organization. Each returned entrypoint is filtered and contains only basic information such as id and name.
     * List entrypoints
     */
    async list21Raw(requestParameters: List21Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Array<Entrypoint>>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling list21.');
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
    async list21(requestParameters: List21Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Array<Entrypoint>> {
        const response = await this.list21Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * User must have the ORGANIZATION_ENTRYPOINT[UPDATE] permission on the specified organization
     * Update the sharding entrypoint
     */
    async update11Raw(requestParameters: Update11Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Entrypoint>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling update11.');
        }

        if (requestParameters.entrypointId === null || requestParameters.entrypointId === undefined) {
            throw new runtime.RequiredError('entrypointId','Required parameter requestParameters.entrypointId was null or undefined when calling update11.');
        }

        if (requestParameters.entrypoint === null || requestParameters.entrypoint === undefined) {
            throw new runtime.RequiredError('entrypoint','Required parameter requestParameters.entrypoint was null or undefined when calling update11.');
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
    async update11(requestParameters: Update11Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Entrypoint> {
        const response = await this.update11Raw(requestParameters, initOverrides);
        return await response.value();
    }

}

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
    Domain,
    DomainFromJSON,
    DomainToJSON,
    NewTag,
    NewTagFromJSON,
    NewTagToJSON,
    Tag,
    TagFromJSON,
    TagToJSON,
    UpdateTag,
    UpdateTagFromJSON,
    UpdateTagToJSON,
} from '../models';

export interface DeleteRequest {
    organizationId: string;
    tag: string;
}

export interface CreateRequest {
    organizationId: string;
    tag: NewTag;
}

export interface Get2Request {
    organizationId: string;
    tag: string;
}

export interface List1Request {
    organizationId: string;
}

export interface UpdateRequest {
    organizationId: string;
    tag: string;
    tag2: UpdateTag;
}

/**
 * 
 */
export class ShardingTagsApi extends runtime.BaseAPI {

    /**
     * User must have the ORGANIZATION_TAG[DELETE] permission on the specified organization
     * Delete the sharding tag
     */
    async _deleteRaw(requestParameters: DeleteRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling _delete.');
        }

        if (requestParameters.tag === null || requestParameters.tag === undefined) {
            throw new runtime.RequiredError('tag','Required parameter requestParameters.tag was null or undefined when calling _delete.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/tags/{tag}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"tag"}}`, encodeURIComponent(String(requestParameters.tag))),
            method: 'DELETE',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have the ORGANIZATION_TAG[DELETE] permission on the specified organization
     * Delete the sharding tag
     */
    async _delete(requestParameters: DeleteRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this._deleteRaw(requestParameters, initOverrides);
    }

    /**
     * User must have the ORGANIZATION_TAG[CREATE] permission on the specified organization
     * Create a sharding tags
     */
    async createRaw(requestParameters: CreateRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling create.');
        }

        if (requestParameters.tag === null || requestParameters.tag === undefined) {
            throw new runtime.RequiredError('tag','Required parameter requestParameters.tag was null or undefined when calling create.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/tags`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))),
            method: 'POST',
            headers: headerParameters,
            query: queryParameters,
            body: NewTagToJSON(requestParameters.tag),
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have the ORGANIZATION_TAG[CREATE] permission on the specified organization
     * Create a sharding tags
     */
    async create(requestParameters: CreateRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.createRaw(requestParameters, initOverrides);
    }

    /**
     * User must have the ORGANIZATION_TAG[READ] permission on the specified organization
     * Get a sharding tag
     */
    async get2Raw(requestParameters: Get2Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Tag>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling get2.');
        }

        if (requestParameters.tag === null || requestParameters.tag === undefined) {
            throw new runtime.RequiredError('tag','Required parameter requestParameters.tag was null or undefined when calling get2.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/tags/{tag}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"tag"}}`, encodeURIComponent(String(requestParameters.tag))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => TagFromJSON(jsonValue));
    }

    /**
     * User must have the ORGANIZATION_TAG[READ] permission on the specified organization
     * Get a sharding tag
     */
    async get2(requestParameters: Get2Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Tag> {
        const response = await this.get2Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * User must have the ORGANIZATION[LIST] permission on the specified organization. Each returned tag is filtered and contains only basic information such as id and name.
     * List sharding tags
     */
    async list1Raw(requestParameters: List1Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Array<Domain>>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling list1.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/tags`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => jsonValue.map(DomainFromJSON));
    }

    /**
     * User must have the ORGANIZATION[LIST] permission on the specified organization. Each returned tag is filtered and contains only basic information such as id and name.
     * List sharding tags
     */
    async list1(requestParameters: List1Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Array<Domain>> {
        const response = await this.list1Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * User must have the ORGANIZATION_TAG[UPDATE] permission on the specified organization
     * Update the sharding tag
     */
    async updateRaw(requestParameters: UpdateRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Tag>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling update.');
        }

        if (requestParameters.tag === null || requestParameters.tag === undefined) {
            throw new runtime.RequiredError('tag','Required parameter requestParameters.tag was null or undefined when calling update.');
        }

        if (requestParameters.tag2 === null || requestParameters.tag2 === undefined) {
            throw new runtime.RequiredError('tag2','Required parameter requestParameters.tag2 was null or undefined when calling update.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/tags/{tag}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"tag"}}`, encodeURIComponent(String(requestParameters.tag))),
            method: 'PUT',
            headers: headerParameters,
            query: queryParameters,
            body: UpdateTagToJSON(requestParameters.tag2),
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => TagFromJSON(jsonValue));
    }

    /**
     * User must have the ORGANIZATION_TAG[UPDATE] permission on the specified organization
     * Update the sharding tag
     */
    async update(requestParameters: UpdateRequest, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Tag> {
        const response = await this.updateRaw(requestParameters, initOverrides);
        return await response.value();
    }

}

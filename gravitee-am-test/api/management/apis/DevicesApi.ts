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
    User,
    UserFromJSON,
    UserToJSON,
} from '../models';

export interface Delete2Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    user: string;
    device: string;
}

export interface List6Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    user: string;
}

/**
 * 
 */
export class DevicesApi extends runtime.BaseAPI {

    /**
     * User must have the DOMAIN_USER_DEVICE[DELETE] permission on the specified domain or DOMAIN_USER_DEVICE[DELETE] permission on the specified environment or DOMAIN_USER_DEVICE[DELETE] permission on the specified organization
     * Delete a device
     */
    async delete2Raw(requestParameters: Delete2Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling delete2.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling delete2.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling delete2.');
        }

        if (requestParameters.user === null || requestParameters.user === undefined) {
            throw new runtime.RequiredError('user','Required parameter requestParameters.user was null or undefined when calling delete2.');
        }

        if (requestParameters.device === null || requestParameters.device === undefined) {
            throw new runtime.RequiredError('device','Required parameter requestParameters.device was null or undefined when calling delete2.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/users/{user}/devices/{device}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"user"}}`, encodeURIComponent(String(requestParameters.user))).replace(`{${"device"}}`, encodeURIComponent(String(requestParameters.device))),
            method: 'DELETE',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have the DOMAIN_USER_DEVICE[DELETE] permission on the specified domain or DOMAIN_USER_DEVICE[DELETE] permission on the specified environment or DOMAIN_USER_DEVICE[DELETE] permission on the specified organization
     * Delete a device
     */
    async delete2(requestParameters: Delete2Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.delete2Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the DOMAIN_USER_DEVICES[LIST] permission on the specified domain or DOMAIN_USER_DEVICES[LIST] permission on the specified environment or DOMAIN_USER_DEVICES[LIST] permission on the specified organization. 
     * List users for a security domain
     */
    async list6Raw(requestParameters: List6Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Set<User>>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling list6.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling list6.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling list6.');
        }

        if (requestParameters.user === null || requestParameters.user === undefined) {
            throw new runtime.RequiredError('user','Required parameter requestParameters.user was null or undefined when calling list6.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/users/{user}/devices`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"user"}}`, encodeURIComponent(String(requestParameters.user))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => new Set(jsonValue.map(UserFromJSON)));
    }

    /**
     * User must have the DOMAIN_USER_DEVICES[LIST] permission on the specified domain or DOMAIN_USER_DEVICES[LIST] permission on the specified environment or DOMAIN_USER_DEVICES[LIST] permission on the specified organization. 
     * List users for a security domain
     */
    async list6(requestParameters: List6Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Set<User>> {
        const response = await this.list6Raw(requestParameters, initOverrides);
        return await response.value();
    }

}

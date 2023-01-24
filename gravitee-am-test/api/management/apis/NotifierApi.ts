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
    ErrorEntity,
    ErrorEntityFromJSON,
    ErrorEntityToJSON,
    NotifierPlugin,
    NotifierPluginFromJSON,
    NotifierPluginToJSON,
} from '../models';

export interface Get35Request {
    notifierId: string;
}

export interface GetSchema9Request {
    notifierId: string;
}

export interface List35Request {
    expand?: Array<string>;
}

/**
 * 
 */
export class NotifierApi extends runtime.BaseAPI {

    /**
     * There is no particular permission needed. User must be authenticated.
     * Get a notifier
     */
    async get35Raw(requestParameters: Get35Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<NotifierPlugin>> {
        if (requestParameters.notifierId === null || requestParameters.notifierId === undefined) {
            throw new runtime.RequiredError('notifierId','Required parameter requestParameters.notifierId was null or undefined when calling get35.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/platform/plugins/notifiers/{notifierId}`.replace(`{${"notifierId"}}`, encodeURIComponent(String(requestParameters.notifierId))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => NotifierPluginFromJSON(jsonValue));
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * Get a notifier
     */
    async get35(requestParameters: Get35Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<NotifierPlugin> {
        const response = await this.get35Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * Get a notifier plugin\'s schema
     */
    async getSchema9Raw(requestParameters: GetSchema9Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<string>> {
        if (requestParameters.notifierId === null || requestParameters.notifierId === undefined) {
            throw new runtime.RequiredError('notifierId','Required parameter requestParameters.notifierId was null or undefined when calling getSchema9.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/platform/plugins/notifiers/{notifierId}/schema`.replace(`{${"notifierId"}}`, encodeURIComponent(String(requestParameters.notifierId))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.TextApiResponse(response) as any;
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * Get a notifier plugin\'s schema
     */
    async getSchema9(requestParameters: GetSchema9Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<string> {
        const response = await this.getSchema9Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * List all available notifier plugins
     */
    async list35Raw(requestParameters: List35Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Array<NotifierPlugin>>> {
        const queryParameters: any = {};

        if (requestParameters.expand) {
            queryParameters['expand'] = requestParameters.expand;
        }

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/platform/plugins/notifiers`,
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => jsonValue.map(NotifierPluginFromJSON));
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * List all available notifier plugins
     */
    async list35(requestParameters: List35Request = {}, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Array<NotifierPlugin>> {
        const response = await this.list35Raw(requestParameters, initOverrides);
        return await response.value();
    }

}

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
    NewReporter,
    NewReporterFromJSON,
    NewReporterToJSON,
    Reporter,
    ReporterFromJSON,
    ReporterToJSON,
    UpdateReporter,
    UpdateReporterFromJSON,
    UpdateReporterToJSON,
} from '../models';

export interface Create5Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    body?: NewReporter;
}

export interface Delete8Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    reporter: string;
}

export interface Get14Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    reporter: string;
}

export interface Get33Request {
    reporter: string;
}

export interface GetSchema3Request {
    reporter: string;
}

export interface List13Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    userProvider?: boolean;
}

export interface Update7Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    reporter: string;
    reporter2: UpdateReporter;
}

/**
 * 
 */
export class ReporterApi extends runtime.BaseAPI {

    /**
     * User must have the DOMAIN_REPORTER[CREATE] permission on the specified domain or DOMAIN_REPORTER[CREATE] permission on the specified environment or DOMAIN_REPORTER[CREATE] permission on the specified organization.
     * Create a reporter for a security domain
     */
    async create5Raw(requestParameters: Create5Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Reporter>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling create5.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling create5.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling create5.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/reporters`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))),
            method: 'POST',
            headers: headerParameters,
            query: queryParameters,
            body: NewReporterToJSON(requestParameters.body),
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => ReporterFromJSON(jsonValue));
    }

    /**
     * User must have the DOMAIN_REPORTER[CREATE] permission on the specified domain or DOMAIN_REPORTER[CREATE] permission on the specified environment or DOMAIN_REPORTER[CREATE] permission on the specified organization.
     * Create a reporter for a security domain
     */
    async create5(requestParameters: Create5Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Reporter> {
        const response = await this.create5Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * User must have the DOMAIN_REPORTER[DELETE] permission on the specified domain or DOMAIN_REPORTER[DELETE] permission on the specified environment or DOMAIN_REPORTER[DELETE] permission on the specified organization
     * Delete a reporter
     */
    async delete8Raw(requestParameters: Delete8Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling delete8.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling delete8.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling delete8.');
        }

        if (requestParameters.reporter === null || requestParameters.reporter === undefined) {
            throw new runtime.RequiredError('reporter','Required parameter requestParameters.reporter was null or undefined when calling delete8.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/reporters/{reporter}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"reporter"}}`, encodeURIComponent(String(requestParameters.reporter))),
            method: 'DELETE',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have the DOMAIN_REPORTER[DELETE] permission on the specified domain or DOMAIN_REPORTER[DELETE] permission on the specified environment or DOMAIN_REPORTER[DELETE] permission on the specified organization
     * Delete a reporter
     */
    async delete8(requestParameters: Delete8Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.delete8Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the DOMAIN_REPORTER[READ] permission on the specified domain or DOMAIN_REPORTER[READ] permission on the specified environment or DOMAIN_REPORTER[READ] permission on the specified organization
     * Get a reporter
     */
    async get14Raw(requestParameters: Get14Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Reporter>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling get14.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling get14.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling get14.');
        }

        if (requestParameters.reporter === null || requestParameters.reporter === undefined) {
            throw new runtime.RequiredError('reporter','Required parameter requestParameters.reporter was null or undefined when calling get14.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/reporters/{reporter}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"reporter"}}`, encodeURIComponent(String(requestParameters.reporter))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => ReporterFromJSON(jsonValue));
    }

    /**
     * User must have the DOMAIN_REPORTER[READ] permission on the specified domain or DOMAIN_REPORTER[READ] permission on the specified environment or DOMAIN_REPORTER[READ] permission on the specified organization
     * Get a reporter
     */
    async get14(requestParameters: Get14Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Reporter> {
        const response = await this.get14Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * Get a reporter plugin
     */
    async get33Raw(requestParameters: Get33Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.reporter === null || requestParameters.reporter === undefined) {
            throw new runtime.RequiredError('reporter','Required parameter requestParameters.reporter was null or undefined when calling get33.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/platform/plugins/reporters/{reporter}`.replace(`{${"reporter"}}`, encodeURIComponent(String(requestParameters.reporter))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * Get a reporter plugin
     */
    async get33(requestParameters: Get33Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.get33Raw(requestParameters, initOverrides);
    }

    /**
     * Get a reporter plugin\'s schema
     */
    async getSchema3Raw(requestParameters: GetSchema3Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.reporter === null || requestParameters.reporter === undefined) {
            throw new runtime.RequiredError('reporter','Required parameter requestParameters.reporter was null or undefined when calling getSchema3.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/platform/plugins/reporters/{reporter}/schema`.replace(`{${"reporter"}}`, encodeURIComponent(String(requestParameters.reporter))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * Get a reporter plugin\'s schema
     */
    async getSchema3(requestParameters: GetSchema3Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.getSchema3Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the DOMAIN_REPORTER[LIST] permission on the specified domain or DOMAIN_REPORTER[LIST] permission on the specified environment or DOMAIN_REPORTER[LIST] permission on the specified organization. Except if user has DOMAIN_REPORTER[READ] permission on the domain, environment or organization, each returned reporter is filtered and contains only basic information such as id and name and type.
     * List registered reporters for a security domain
     */
    async list13Raw(requestParameters: List13Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Array<Reporter>>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling list13.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling list13.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling list13.');
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
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/reporters`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => jsonValue.map(ReporterFromJSON));
    }

    /**
     * User must have the DOMAIN_REPORTER[LIST] permission on the specified domain or DOMAIN_REPORTER[LIST] permission on the specified environment or DOMAIN_REPORTER[LIST] permission on the specified organization. Except if user has DOMAIN_REPORTER[READ] permission on the domain, environment or organization, each returned reporter is filtered and contains only basic information such as id and name and type.
     * List registered reporters for a security domain
     */
    async list13(requestParameters: List13Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Array<Reporter>> {
        const response = await this.list13Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * List reporter plugins
     */
    async list34Raw(initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/platform/plugins/reporters`,
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * There is no particular permission needed. User must be authenticated.
     * List reporter plugins
     */
    async list34(initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.list34Raw(initOverrides);
    }

    /**
     * User must have the DOMAIN_REPORTER[UPDATE] permission on the specified domain or DOMAIN_REPORTER[UPDATE] permission on the specified environment or DOMAIN_REPORTER[UPDATE] permission on the specified organization
     * Update a reporter
     */
    async update7Raw(requestParameters: Update7Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Reporter>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling update7.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling update7.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling update7.');
        }

        if (requestParameters.reporter === null || requestParameters.reporter === undefined) {
            throw new runtime.RequiredError('reporter','Required parameter requestParameters.reporter was null or undefined when calling update7.');
        }

        if (requestParameters.reporter2 === null || requestParameters.reporter2 === undefined) {
            throw new runtime.RequiredError('reporter2','Required parameter requestParameters.reporter2 was null or undefined when calling update7.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/reporters/{reporter}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"reporter"}}`, encodeURIComponent(String(requestParameters.reporter))),
            method: 'PUT',
            headers: headerParameters,
            query: queryParameters,
            body: UpdateReporterToJSON(requestParameters.reporter2),
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => ReporterFromJSON(jsonValue));
    }

    /**
     * User must have the DOMAIN_REPORTER[UPDATE] permission on the specified domain or DOMAIN_REPORTER[UPDATE] permission on the specified environment or DOMAIN_REPORTER[UPDATE] permission on the specified organization
     * Update a reporter
     */
    async update7(requestParameters: Update7Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Reporter> {
        const response = await this.update7Raw(requestParameters, initOverrides);
        return await response.value();
    }

}

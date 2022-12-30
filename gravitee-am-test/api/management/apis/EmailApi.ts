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
    Email,
    EmailFromJSON,
    EmailToJSON,
    NewEmail,
    NewEmailFromJSON,
    NewEmailToJSON,
    UpdateEmail,
    UpdateEmailFromJSON,
    UpdateEmailToJSON,
} from '../models';

export interface Create1Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    application: string;
    email: NewEmail;
}

export interface Create4Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    email: NewEmail;
}

export interface Delete4Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    application: string;
    email: string;
}

export interface Delete7Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    email: string;
}

export interface Get12Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    template: Get12TemplateEnum;
}

export interface Get6Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    application: string;
    template: Get6TemplateEnum;
}

export interface Update1Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    application: string;
    email: string;
    email2: UpdateEmail;
}

export interface Update4Request {
    organizationId: string;
    environmentId: string;
    domain: string;
    email: string;
    email2: UpdateEmail;
}

/**
 * 
 */
export class EmailApi extends runtime.BaseAPI {

    /**
     * User must have APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified application or APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified domain or APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified environment or APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified organization
     * Create a email for an application
     */
    async create1Raw(requestParameters: Create1Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling create1.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling create1.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling create1.');
        }

        if (requestParameters.application === null || requestParameters.application === undefined) {
            throw new runtime.RequiredError('application','Required parameter requestParameters.application was null or undefined when calling create1.');
        }

        if (requestParameters.email === null || requestParameters.email === undefined) {
            throw new runtime.RequiredError('email','Required parameter requestParameters.email was null or undefined when calling create1.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/emails`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"application"}}`, encodeURIComponent(String(requestParameters.application))),
            method: 'POST',
            headers: headerParameters,
            query: queryParameters,
            body: NewEmailToJSON(requestParameters.email),
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified application or APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified domain or APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified environment or APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified organization
     * Create a email for an application
     */
    async create1(requestParameters: Create1Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.create1Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the DOMAIN_EMAIL_TEMPLATE[CREATE] permission on the specified domain or DOMAIN_EMAIL_TEMPLATE[CREATE] permission on the specified environment or DOMAIN_EMAIL_TEMPLATE[CREATE] permission on the specified organization
     * Create a email
     */
    async create4Raw(requestParameters: Create4Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling create4.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling create4.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling create4.');
        }

        if (requestParameters.email === null || requestParameters.email === undefined) {
            throw new runtime.RequiredError('email','Required parameter requestParameters.email was null or undefined when calling create4.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/emails`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))),
            method: 'POST',
            headers: headerParameters,
            query: queryParameters,
            body: NewEmailToJSON(requestParameters.email),
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have the DOMAIN_EMAIL_TEMPLATE[CREATE] permission on the specified domain or DOMAIN_EMAIL_TEMPLATE[CREATE] permission on the specified environment or DOMAIN_EMAIL_TEMPLATE[CREATE] permission on the specified organization
     * Create a email
     */
    async create4(requestParameters: Create4Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.create4Raw(requestParameters, initOverrides);
    }

    /**
     * User must have APPLICATION_EMAIL_TEMPLATE[DELETE] permission on the specified application or APPLICATION_EMAIL_TEMPLATE[DELETE] permission on the specified domain or APPLICATION_EMAIL_TEMPLATE[DELETE] permission on the specified environment or APPLICATION_EMAIL_TEMPLATE[DELETE] permission on the specified organization
     * Delete an email for an application
     */
    async delete4Raw(requestParameters: Delete4Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling delete4.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling delete4.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling delete4.');
        }

        if (requestParameters.application === null || requestParameters.application === undefined) {
            throw new runtime.RequiredError('application','Required parameter requestParameters.application was null or undefined when calling delete4.');
        }

        if (requestParameters.email === null || requestParameters.email === undefined) {
            throw new runtime.RequiredError('email','Required parameter requestParameters.email was null or undefined when calling delete4.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/emails/{email}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"application"}}`, encodeURIComponent(String(requestParameters.application))).replace(`{${"email"}}`, encodeURIComponent(String(requestParameters.email))),
            method: 'DELETE',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have APPLICATION_EMAIL_TEMPLATE[DELETE] permission on the specified application or APPLICATION_EMAIL_TEMPLATE[DELETE] permission on the specified domain or APPLICATION_EMAIL_TEMPLATE[DELETE] permission on the specified environment or APPLICATION_EMAIL_TEMPLATE[DELETE] permission on the specified organization
     * Delete an email for an application
     */
    async delete4(requestParameters: Delete4Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.delete4Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the DOMAIN_EMAIL_TEMPLATE[DELETE] permission on the specified domain or DOMAIN_EMAIL_TEMPLATE[DELETE] permission on the specified environment or DOMAIN_EMAIL_TEMPLATE[DELETE] permission on the specified organization
     * Delete an email
     */
    async delete7Raw(requestParameters: Delete7Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling delete7.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling delete7.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling delete7.');
        }

        if (requestParameters.email === null || requestParameters.email === undefined) {
            throw new runtime.RequiredError('email','Required parameter requestParameters.email was null or undefined when calling delete7.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/emails/{email}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"email"}}`, encodeURIComponent(String(requestParameters.email))),
            method: 'DELETE',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have the DOMAIN_EMAIL_TEMPLATE[DELETE] permission on the specified domain or DOMAIN_EMAIL_TEMPLATE[DELETE] permission on the specified environment or DOMAIN_EMAIL_TEMPLATE[DELETE] permission on the specified organization
     * Delete an email
     */
    async delete7(requestParameters: Delete7Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.delete7Raw(requestParameters, initOverrides);
    }

    /**
     * User must have the DOMAIN_EMAIL_TEMPLATE[READ] permission on the specified domain or DOMAIN_EMAIL_TEMPLATE[READ] permission on the specified environment or DOMAIN_EMAIL_TEMPLATE[READ] permission on the specified organization
     * Find a email
     */
    async get12Raw(requestParameters: Get12Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling get12.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling get12.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling get12.');
        }

        if (requestParameters.template === null || requestParameters.template === undefined) {
            throw new runtime.RequiredError('template','Required parameter requestParameters.template was null or undefined when calling get12.');
        }

        const queryParameters: any = {};

        if (requestParameters.template !== undefined) {
            queryParameters['template'] = requestParameters.template;
        }

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/emails`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have the DOMAIN_EMAIL_TEMPLATE[READ] permission on the specified domain or DOMAIN_EMAIL_TEMPLATE[READ] permission on the specified environment or DOMAIN_EMAIL_TEMPLATE[READ] permission on the specified organization
     * Find a email
     */
    async get12(requestParameters: Get12Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.get12Raw(requestParameters, initOverrides);
    }

    /**
     * User must have APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified application or APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified domain or APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified environment or APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified organization
     * Find a email for an application
     */
    async get6Raw(requestParameters: Get6Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling get6.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling get6.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling get6.');
        }

        if (requestParameters.application === null || requestParameters.application === undefined) {
            throw new runtime.RequiredError('application','Required parameter requestParameters.application was null or undefined when calling get6.');
        }

        if (requestParameters.template === null || requestParameters.template === undefined) {
            throw new runtime.RequiredError('template','Required parameter requestParameters.template was null or undefined when calling get6.');
        }

        const queryParameters: any = {};

        if (requestParameters.template !== undefined) {
            queryParameters['template'] = requestParameters.template;
        }

        const headerParameters: runtime.HTTPHeaders = {};

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/emails`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"application"}}`, encodeURIComponent(String(requestParameters.application))),
            method: 'GET',
            headers: headerParameters,
            query: queryParameters,
        }, initOverrides);

        return new runtime.VoidApiResponse(response);
    }

    /**
     * User must have APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified application or APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified domain or APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified environment or APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified organization
     * Find a email for an application
     */
    async get6(requestParameters: Get6Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<void> {
        await this.get6Raw(requestParameters, initOverrides);
    }

    /**
     * User must have APPLICATION_EMAIL_TEMPLATE[UPDATE] permission on the specified application or APPLICATION_EMAIL_TEMPLATE[UPDATE] permission on the specified domain or APPLICATION_EMAIL_TEMPLATE[UPDATE] permission on the specified environment or APPLICATION_EMAIL_TEMPLATE[UPDATE] permission on the specified organization
     * Update an email for an application
     */
    async update1Raw(requestParameters: Update1Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Email>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling update1.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling update1.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling update1.');
        }

        if (requestParameters.application === null || requestParameters.application === undefined) {
            throw new runtime.RequiredError('application','Required parameter requestParameters.application was null or undefined when calling update1.');
        }

        if (requestParameters.email === null || requestParameters.email === undefined) {
            throw new runtime.RequiredError('email','Required parameter requestParameters.email was null or undefined when calling update1.');
        }

        if (requestParameters.email2 === null || requestParameters.email2 === undefined) {
            throw new runtime.RequiredError('email2','Required parameter requestParameters.email2 was null or undefined when calling update1.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/emails/{email}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"application"}}`, encodeURIComponent(String(requestParameters.application))).replace(`{${"email"}}`, encodeURIComponent(String(requestParameters.email))),
            method: 'PUT',
            headers: headerParameters,
            query: queryParameters,
            body: UpdateEmailToJSON(requestParameters.email2),
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => EmailFromJSON(jsonValue));
    }

    /**
     * User must have APPLICATION_EMAIL_TEMPLATE[UPDATE] permission on the specified application or APPLICATION_EMAIL_TEMPLATE[UPDATE] permission on the specified domain or APPLICATION_EMAIL_TEMPLATE[UPDATE] permission on the specified environment or APPLICATION_EMAIL_TEMPLATE[UPDATE] permission on the specified organization
     * Update an email for an application
     */
    async update1(requestParameters: Update1Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Email> {
        const response = await this.update1Raw(requestParameters, initOverrides);
        return await response.value();
    }

    /**
     * User must have the DOMAIN_EMAIL_TEMPLATE[UPDATE] permission on the specified domain or DOMAIN_EMAIL_TEMPLATE[UPDATE] permission on the specified environment or DOMAIN_EMAIL_TEMPLATE[UPDATE] permission on the specified organization
     * Update an email
     */
    async update4Raw(requestParameters: Update4Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<runtime.ApiResponse<Email>> {
        if (requestParameters.organizationId === null || requestParameters.organizationId === undefined) {
            throw new runtime.RequiredError('organizationId','Required parameter requestParameters.organizationId was null or undefined when calling update4.');
        }

        if (requestParameters.environmentId === null || requestParameters.environmentId === undefined) {
            throw new runtime.RequiredError('environmentId','Required parameter requestParameters.environmentId was null or undefined when calling update4.');
        }

        if (requestParameters.domain === null || requestParameters.domain === undefined) {
            throw new runtime.RequiredError('domain','Required parameter requestParameters.domain was null or undefined when calling update4.');
        }

        if (requestParameters.email === null || requestParameters.email === undefined) {
            throw new runtime.RequiredError('email','Required parameter requestParameters.email was null or undefined when calling update4.');
        }

        if (requestParameters.email2 === null || requestParameters.email2 === undefined) {
            throw new runtime.RequiredError('email2','Required parameter requestParameters.email2 was null or undefined when calling update4.');
        }

        const queryParameters: any = {};

        const headerParameters: runtime.HTTPHeaders = {};

        headerParameters['Content-Type'] = 'application/json';

        if (this.configuration && this.configuration.apiKey) {
            headerParameters["Authorization"] = this.configuration.apiKey("Authorization"); // gravitee-auth authentication
        }

        const response = await this.request({
            path: `/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/emails/{email}`.replace(`{${"organizationId"}}`, encodeURIComponent(String(requestParameters.organizationId))).replace(`{${"environmentId"}}`, encodeURIComponent(String(requestParameters.environmentId))).replace(`{${"domain"}}`, encodeURIComponent(String(requestParameters.domain))).replace(`{${"email"}}`, encodeURIComponent(String(requestParameters.email))),
            method: 'PUT',
            headers: headerParameters,
            query: queryParameters,
            body: UpdateEmailToJSON(requestParameters.email2),
        }, initOverrides);

        return new runtime.JSONApiResponse(response, (jsonValue) => EmailFromJSON(jsonValue));
    }

    /**
     * User must have the DOMAIN_EMAIL_TEMPLATE[UPDATE] permission on the specified domain or DOMAIN_EMAIL_TEMPLATE[UPDATE] permission on the specified environment or DOMAIN_EMAIL_TEMPLATE[UPDATE] permission on the specified organization
     * Update an email
     */
    async update4(requestParameters: Update4Request, initOverrides?: RequestInit | runtime.InitOverideFunction): Promise<Email> {
        const response = await this.update4Raw(requestParameters, initOverrides);
        return await response.value();
    }

}

/**
 * @export
 */
export const Get12TemplateEnum = {
    Login: 'LOGIN',
    Registration: 'REGISTRATION',
    RegistrationConfirmation: 'REGISTRATION_CONFIRMATION',
    ForgotPassword: 'FORGOT_PASSWORD',
    ResetPassword: 'RESET_PASSWORD',
    Oauth2UserConsent: 'OAUTH2_USER_CONSENT',
    MfaEnroll: 'MFA_ENROLL',
    MfaChallenge: 'MFA_CHALLENGE',
    MfaChallengeAlternatives: 'MFA_CHALLENGE_ALTERNATIVES',
    MfaRecoveryCode: 'MFA_RECOVERY_CODE',
    BlockedAccount: 'BLOCKED_ACCOUNT',
    CompleteProfile: 'COMPLETE_PROFILE',
    WebauthnRegister: 'WEBAUTHN_REGISTER',
    WebauthnLogin: 'WEBAUTHN_LOGIN',
    IdentifierFirstLogin: 'IDENTIFIER_FIRST_LOGIN',
    Error: 'ERROR',
    CertificateExpiration: 'CERTIFICATE_EXPIRATION',
    VerifyAttempt: 'VERIFY_ATTEMPT'
} as const;
export type Get12TemplateEnum = typeof Get12TemplateEnum[keyof typeof Get12TemplateEnum];
/**
 * @export
 */
export const Get6TemplateEnum = {
    Login: 'LOGIN',
    Registration: 'REGISTRATION',
    RegistrationConfirmation: 'REGISTRATION_CONFIRMATION',
    ForgotPassword: 'FORGOT_PASSWORD',
    ResetPassword: 'RESET_PASSWORD',
    Oauth2UserConsent: 'OAUTH2_USER_CONSENT',
    MfaEnroll: 'MFA_ENROLL',
    MfaChallenge: 'MFA_CHALLENGE',
    MfaChallengeAlternatives: 'MFA_CHALLENGE_ALTERNATIVES',
    MfaRecoveryCode: 'MFA_RECOVERY_CODE',
    BlockedAccount: 'BLOCKED_ACCOUNT',
    CompleteProfile: 'COMPLETE_PROFILE',
    WebauthnRegister: 'WEBAUTHN_REGISTER',
    WebauthnLogin: 'WEBAUTHN_LOGIN',
    IdentifierFirstLogin: 'IDENTIFIER_FIRST_LOGIN',
    Error: 'ERROR',
    CertificateExpiration: 'CERTIFICATE_EXPIRATION',
    VerifyAttempt: 'VERIFY_ATTEMPT'
} as const;
export type Get6TemplateEnum = typeof Get6TemplateEnum[keyof typeof Get6TemplateEnum];

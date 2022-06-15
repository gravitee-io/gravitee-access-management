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

import { exists, mapValues } from '../runtime';
import {
    Step,
    StepFromJSON,
    StepFromJSONTyped,
    StepToJSON,
} from './Step';

/**
 * 
 * @export
 * @interface FlowEntity
 */
export interface FlowEntity {
    /**
     * 
     * @type {string}
     * @memberof FlowEntity
     */
    id?: string;
    /**
     * 
     * @type {string}
     * @memberof FlowEntity
     */
    name?: string;
    /**
     * 
     * @type {Array<Step>}
     * @memberof FlowEntity
     */
    pre?: Array<Step>;
    /**
     * 
     * @type {Array<Step>}
     * @memberof FlowEntity
     */
    post?: Array<Step>;
    /**
     * 
     * @type {boolean}
     * @memberof FlowEntity
     */
    enabled?: boolean;
    /**
     * 
     * @type {string}
     * @memberof FlowEntity
     */
    type?: FlowEntityTypeEnum;
    /**
     * 
     * @type {string}
     * @memberof FlowEntity
     */
    condition?: string;
    /**
     * 
     * @type {Date}
     * @memberof FlowEntity
     */
    createdAt?: Date;
    /**
     * 
     * @type {Date}
     * @memberof FlowEntity
     */
    updatedAt?: Date;
}


/**
 * @export
 */
export const FlowEntityTypeEnum = {
    Root: 'ROOT',
    LoginIdentifier: 'LOGIN_IDENTIFIER',
    Login: 'LOGIN',
    Consent: 'CONSENT',
    Register: 'REGISTER',
    ResetPassword: 'RESET_PASSWORD',
    RegistrationConfirmation: 'REGISTRATION_CONFIRMATION'
} as const;
export type FlowEntityTypeEnum = typeof FlowEntityTypeEnum[keyof typeof FlowEntityTypeEnum];


export function FlowEntityFromJSON(json: any): FlowEntity {
    return FlowEntityFromJSONTyped(json, false);
}

export function FlowEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): FlowEntity {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'id': !exists(json, 'id') ? undefined : json['id'],
        'name': !exists(json, 'name') ? undefined : json['name'],
        'pre': !exists(json, 'pre') ? undefined : ((json['pre'] as Array<any>).map(StepFromJSON)),
        'post': !exists(json, 'post') ? undefined : ((json['post'] as Array<any>).map(StepFromJSON)),
        'enabled': !exists(json, 'enabled') ? undefined : json['enabled'],
        'type': !exists(json, 'type') ? undefined : json['type'],
        'condition': !exists(json, 'condition') ? undefined : json['condition'],
        'createdAt': !exists(json, 'createdAt') ? undefined : (new Date(json['createdAt'])),
        'updatedAt': !exists(json, 'updatedAt') ? undefined : (new Date(json['updatedAt'])),
    };
}

export function FlowEntityToJSON(value?: FlowEntity | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'id': value.id,
        'name': value.name,
        'pre': value.pre === undefined ? undefined : ((value.pre as Array<any>).map(StepToJSON)),
        'post': value.post === undefined ? undefined : ((value.post as Array<any>).map(StepToJSON)),
        'enabled': value.enabled,
        'type': value.type,
        'condition': value.condition,
        'createdAt': value.createdAt === undefined ? undefined : (value.createdAt.toISOString()),
        'updatedAt': value.updatedAt === undefined ? undefined : (value.updatedAt.toISOString()),
    };
}


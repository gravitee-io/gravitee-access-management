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
/**
 * 
 * @export
 * @interface JWK
 */
export interface JWK {
    /**
     * 
     * @type {string}
     * @memberof JWK
     */
    kty?: string;
    /**
     * 
     * @type {string}
     * @memberof JWK
     */
    use?: string;
    /**
     * 
     * @type {Set<string>}
     * @memberof JWK
     */
    keyOps?: Set<string>;
    /**
     * 
     * @type {string}
     * @memberof JWK
     */
    alg?: string;
    /**
     * 
     * @type {string}
     * @memberof JWK
     */
    kid?: string;
    /**
     * 
     * @type {string}
     * @memberof JWK
     */
    x5u?: string;
    /**
     * 
     * @type {Set<string>}
     * @memberof JWK
     */
    x5c?: Set<string>;
    /**
     * 
     * @type {string}
     * @memberof JWK
     */
    x5t?: string;
    /**
     * 
     * @type {string}
     * @memberof JWK
     */
    x5tS256?: string;
}

export function JWKFromJSON(json: any): JWK {
    return JWKFromJSONTyped(json, false);
}

export function JWKFromJSONTyped(json: any, ignoreDiscriminator: boolean): JWK {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'kty': !exists(json, 'kty') ? undefined : json['kty'],
        'use': !exists(json, 'use') ? undefined : json['use'],
        'keyOps': !exists(json, 'keyOps') ? undefined : json['keyOps'],
        'alg': !exists(json, 'alg') ? undefined : json['alg'],
        'kid': !exists(json, 'kid') ? undefined : json['kid'],
        'x5u': !exists(json, 'x5u') ? undefined : json['x5u'],
        'x5c': !exists(json, 'x5c') ? undefined : json['x5c'],
        'x5t': !exists(json, 'x5t') ? undefined : json['x5t'],
        'x5tS256': !exists(json, 'x5tS256') ? undefined : json['x5tS256'],
    };
}

export function JWKToJSON(value?: JWK | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'kty': value.kty,
        'use': value.use,
        'keyOps': value.keyOps,
        'alg': value.alg,
        'kid': value.kid,
        'x5u': value.x5u,
        'x5c': value.x5c,
        'x5t': value.x5t,
        'x5tS256': value.x5tS256,
    };
}


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
    PatchChallengeSettings,
    PatchChallengeSettingsFromJSON,
    PatchChallengeSettingsFromJSONTyped,
    PatchChallengeSettingsToJSON,
} from './PatchChallengeSettings';
import {
    PatchEnrollSettings,
    PatchEnrollSettingsFromJSON,
    PatchEnrollSettingsFromJSONTyped,
    PatchEnrollSettingsToJSON,
} from './PatchEnrollSettings';
import {
    PatchEnrollmentSettings,
    PatchEnrollmentSettingsFromJSON,
    PatchEnrollmentSettingsFromJSONTyped,
    PatchEnrollmentSettingsToJSON,
} from './PatchEnrollmentSettings';
import {
    PatchRememberDeviceSettings,
    PatchRememberDeviceSettingsFromJSON,
    PatchRememberDeviceSettingsFromJSONTyped,
    PatchRememberDeviceSettingsToJSON,
} from './PatchRememberDeviceSettings';

/**
 * 
 * @export
 * @interface PatchMFASettings
 */
export interface PatchMFASettings {
    /**
     * 
     * @type {string}
     * @memberof PatchMFASettings
     */
    loginRule?: string;
    /**
     * 
     * @type {string}
     * @memberof PatchMFASettings
     */
    stepUpAuthenticationRule?: string;
    /**
     * 
     * @type {string}
     * @memberof PatchMFASettings
     */
    adaptiveAuthenticationRule?: string;
    /**
     * 
     * @type {PatchRememberDeviceSettings}
     * @memberof PatchMFASettings
     */
    rememberDevice?: PatchRememberDeviceSettings;
    /**
     * 
     * @type {PatchEnrollmentSettings}
     * @memberof PatchMFASettings
     */
    enrollment?: PatchEnrollmentSettings;
    /**
     * 
     * @type {PatchEnrollSettings}
     * @memberof PatchMFASettings
     */
    enroll?: PatchEnrollSettings;
    /**
     * 
     * @type {PatchChallengeSettings}
     * @memberof PatchMFASettings
     */
    challenge?: PatchChallengeSettings;
}

export function PatchMFASettingsFromJSON(json: any): PatchMFASettings {
    return PatchMFASettingsFromJSONTyped(json, false);
}

export function PatchMFASettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchMFASettings {
    if (json == null) {
        return json;
    }
    return {
        
        'loginRule': !exists(json, 'loginRule') ? undefined : json['loginRule'],
        'stepUpAuthenticationRule': !exists(json, 'stepUpAuthenticationRule') ? undefined : json['stepUpAuthenticationRule'],
        'adaptiveAuthenticationRule': !exists(json, 'adaptiveAuthenticationRule') ? undefined : json['adaptiveAuthenticationRule'],
        'rememberDevice': !exists(json, 'rememberDevice') ? undefined : PatchRememberDeviceSettingsFromJSON(json['rememberDevice']),
        'enrollment': !exists(json, 'enrollment') ? undefined : PatchEnrollmentSettingsFromJSON(json['enrollment']),
        'enroll': !exists(json, 'enroll') ? undefined : PatchEnrollSettingsFromJSON(json['enroll']),
        'challenge': !exists(json, 'challenge') ? undefined : PatchChallengeSettingsFromJSON(json['challenge']),
    };
}

export function PatchMFASettingsToJSON(value?: PatchMFASettings | null): any {
    if (value == null) {
        return value;
    }
    return {
        
        'loginRule': value.loginRule,
        'stepUpAuthenticationRule': value.stepUpAuthenticationRule,
        'adaptiveAuthenticationRule': value.adaptiveAuthenticationRule,
        'rememberDevice': PatchRememberDeviceSettingsToJSON(value.rememberDevice),
        'enrollment': PatchEnrollmentSettingsToJSON(value.enrollment),
        'enroll': PatchEnrollSettingsToJSON(value.enroll),
        'challenge': PatchChallengeSettingsToJSON(value.challenge),
    };
}


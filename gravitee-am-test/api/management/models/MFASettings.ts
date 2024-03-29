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

import { exists } from '../runtime';
import {
    ChallengeSettings,
    ChallengeSettingsFromJSON,
    ChallengeSettingsToJSON,
} from './ChallengeSettings';
import {
    EnrollSettings,
    EnrollSettingsFromJSON,
    EnrollSettingsToJSON,
} from './EnrollSettings';
import {
    EnrollmentSettings,
    EnrollmentSettingsFromJSON,
    EnrollmentSettingsToJSON,
} from './EnrollmentSettings';
import {
    RememberDeviceSettings,
    RememberDeviceSettingsFromJSON,
    RememberDeviceSettingsToJSON,
} from './RememberDeviceSettings';
import {
    StepUpAuthenticationSettings,
    StepUpAuthenticationSettingsFromJSON,
    StepUpAuthenticationSettingsToJSON,
} from './StepUpAuthenticationSettings';
import { FactorSettings, FactorSettingsFromJSON, FactorSettingsFromJSONTyped } from './FactorSettings';

/**
 *
 * @export
 * @interface MFASettings
 */
export interface MFASettings {
    /**
     *
     * @type {string}
     * @memberof MFASettings
     */
    loginRule?: string;

    factor: FactorSettings;
    /**
     *
     * @type {string}
     * @memberof MFASettings
     */
    stepUpAuthenticationRule?: string;
    /**
     *
     * @type {StepUpAuthenticationSettings}
     * @memberof MFASettings
     */
    stepUpAuthentication?: StepUpAuthenticationSettings;
    /**
     *
     * @type {string}
     * @memberof MFASettings
     */
    adaptiveAuthenticationRule?: string;
    /**
     *
     * @type {RememberDeviceSettings}
     * @memberof MFASettings
     */
    rememberDevice?: RememberDeviceSettings;
    /**
     *
     * @type {EnrollmentSettings}
     * @memberof MFASettings
     */
    enrollment?: EnrollmentSettings;
    /**
     *
     * @type {ChallengeSettings}
     * @memberof MFASettings
     */
    challenge?: ChallengeSettings;
    /**
     *
     * @type {EnrollSettings}
     * @memberof MFASettings
     */
    enroll?: EnrollSettings;
}

export function MFASettingsFromJSON(json: any): MFASettings {
    return MFASettingsFromJSONTyped(json, false);
}

export function MFASettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): MFASettings {
    if (json == null) {
        return json;
    }
    return {
        'loginRule': !exists(json, 'loginRule') ? undefined : json['loginRule'],
        'factor': !exists(json, 'factors') ? undefined : FactorSettingsFromJSON(json['factors']),
        'stepUpAuthenticationRule': !exists(json, 'stepUpAuthenticationRule') ? undefined : json['stepUpAuthenticationRule'],
        'stepUpAuthentication': !exists(json, 'stepUpAuthentication') ? undefined : StepUpAuthenticationSettingsFromJSON(json['stepUpAuthentication']),
        'adaptiveAuthenticationRule': !exists(json, 'adaptiveAuthenticationRule') ? undefined : json['adaptiveAuthenticationRule'],
        'rememberDevice': !exists(json, 'rememberDevice') ? undefined : RememberDeviceSettingsFromJSON(json['rememberDevice']),
        'enrollment': !exists(json, 'enrollment') ? undefined : EnrollmentSettingsFromJSON(json['enrollment']),
        'challenge': !exists(json, 'challenge') ? undefined : ChallengeSettingsFromJSON(json['challenge']),
        'enroll': !exists(json, 'enroll') ? undefined : EnrollSettingsFromJSON(json['enroll']),
    };
}

export function MFASettingsToJSON(value?: MFASettings | null): any {
    if (value == null) {
        return value;
    }
    return {
        'loginRule': value.loginRule,
        'factor': value.factor,
        'stepUpAuthenticationRule': value.stepUpAuthenticationRule,
        'stepUpAuthentication': StepUpAuthenticationSettingsToJSON(value.stepUpAuthentication),
        'adaptiveAuthenticationRule': value.adaptiveAuthenticationRule,
        'rememberDevice': RememberDeviceSettingsToJSON(value.rememberDevice),
        'enrollment': EnrollmentSettingsToJSON(value.enrollment),
        'challenge': ChallengeSettingsToJSON(value.challenge),
        'enroll': EnrollSettingsToJSON(value.enroll),
    };
}


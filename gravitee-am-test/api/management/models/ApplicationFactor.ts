/* tslint:disable */
/* eslint-disable */

import { exists } from '../runtime';

export interface ApplicationPathFactor {
    id?: string;
    selectionRule?: string;
}

export function PathFactorFromJSON(json: any): ApplicationPathFactor {
    return ApplicationPathFactorFromJSONTyped(json, false);
}

export function ApplicationPathFactorFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationPathFactor {
    if (json == null) {
        return json;
    }
    return {
        'id': !exists(json, 'id') ? undefined : json['id'],
        'selectionRule': !exists(json, 'selectionRule') ? undefined : json['selectionRule']
    };
}

export function ApplicationFactorSettingsToJSON(value?: ApplicationPathFactor | null): any {
    if (value == null) {
        return value;
    }
    return {
        'id': value.id,
        'selectionRule': value.selectionRule
    };
}


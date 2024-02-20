/* tslint:disable */
/* eslint-disable */

import { exists } from '../runtime';

export interface PathFactor {
    id?: string;
    selectionRule?: string;
}

export function PathFactorFromJSON(json: any): PathFactor {
    return PathApplicationFactorFromJSONTyped(json, false);
}

export function PathApplicationFactorFromJSONTyped(json: any, ignoreDiscriminator: boolean): PathFactor {
    if (json == null) {
        return json;
    }
    return {
        'id': !exists(json, 'id') ? undefined : json['id'],
        'selectionRule': !exists(json, 'selectionRule') ? undefined : json['selectionRule']
    };
}

export function PathApplicationFactorSettingsToJSON(value?: PathFactor | null): any {
    if (value == null) {
        return value;
    }
    return {
        'id': value.id,
        'selectionRule': value.selectionRule
    };
}


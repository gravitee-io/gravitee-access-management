/* tslint:disable */
/* eslint-disable */

import { exists } from '../runtime';
import { PathFactor, PathApplicationFactorSettingsToJSON } from './PatchApplicationFactor';

export interface PatchFactorSettings {
    defaultFactorId?: string;
    applicationFactors?: PathFactor[];
}

export function PatchFactorSettingsFromJSON(json: any): PatchFactorSettings {
    return PatchFactorSettingsFromJSONTyped(json, false);
}

export function PatchFactorSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchFactorSettings {
    if (json == null) {
        return json;
    }
    return {
        'defaultFactorId': !exists(json, 'defaultFactorId') ? undefined : json['defaultFactorId'],
        'applicationFactors': !exists(json, 'factors') ? undefined : PathApplicationFactorSettingsToJSON(json['applicationFactors'])
    };
}


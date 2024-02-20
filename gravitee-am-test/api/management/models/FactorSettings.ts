/* tslint:disable */
/* eslint-disable */

import { exists } from '../runtime';
import { PathFactor, PathApplicationFactorSettingsToJSON } from './PatchApplicationFactor';
import { ApplicationFactorSettingsToJSON } from './ApplicationFactor';

export interface FactorSettings {
    defaultFactorId?: string;
    applicationFactors?: PathFactor[];
}

export function FactorSettingsFromJSON(json: any): FactorSettings {
    return FactorSettingsFromJSONTyped(json, false);
}

export function FactorSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): FactorSettings {
    if (json == null) {
        return json;
    }
    return {
        'defaultFactorId': !exists(json, 'defaultFactorId') ? undefined : json['defaultFactorId'],
        'applicationFactors': !exists(json, 'factors') ? undefined : ApplicationFactorSettingsToJSON(json['applicationFactors'])
    };
}


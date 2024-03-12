/* tslint:disable */
/* eslint-disable */

import { exists } from '../runtime';

export interface PatchStepUpAuthSettings {
  active: boolean;
  stepUpAuthenticationRule?: string;
}

export function PatchStepUpAuthSettings(json: any): PatchStepUpAuthSettings {
  return PatchStepUpAuthSettingsFromJSONTyped(json, false);
}

export function PatchStepUpAuthSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchStepUpAuthSettings {
  if (json == null) {
    return json;
  }
  return {
    active: json['active'],
    stepUpAuthenticationRule: !exists(json, 'stepUpAuthenticationRule') ? '' : json['stepUpAuthenticationRule'],
  };
}

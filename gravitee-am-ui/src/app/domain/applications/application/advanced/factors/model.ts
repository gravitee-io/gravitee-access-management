/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { LicenseOptions } from '@gravitee/ui-particles-angular';
import { Observable } from 'rxjs';

export interface Challenge {
  active: boolean;
  adaptiveMfaRule: string;
  riskAssessment: any;
  challengeRule: string;
  type: string;
}

export interface Enroll {
  active: boolean;
  forceEnrollment: boolean;
  skipTimeSeconds: number;
  enrollmentRule: string;
  // skipEnrollmentRule: string;
  type: string;
}

export interface RememberDevice {
  active: boolean;
  skipRememberDevice: boolean;
  deviceIdentifierId: string;
  expirationTimeSeconds: any;
}

export interface ModeOption {
  label: string;
  value: string;
  message: string;
  licenseOptions?: LicenseOptions;
  isMissingFeature$?: Observable<boolean>;
  warning?: string;
  warningLink?: string;
}

export interface StepUpAuth {
  stepUpAuthenticationRule: string;
  active: boolean;
}

export interface MfaFactor {
  id: string;
  name: string;
  factorType: string;
  selected: boolean;
  isDefault: boolean;
}

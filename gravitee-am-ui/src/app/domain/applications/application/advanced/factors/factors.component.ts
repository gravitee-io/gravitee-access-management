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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Challenge, Enroll, RememberDevice } from './model';

import { ApplicationService } from '../../../../../services/application.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { FactorService } from '../../../../../services/factor.service';
import { AuthService } from '../../../../../services/auth.service';

@Component({
  selector: 'app-application-factors',
  templateUrl: './factors.component.html',
  styleUrls: ['./factors.component.scss'],
})
export class ApplicationFactorsComponent implements OnInit {
  private domainId: string;

  application: any;
  factors: any[];
  deviceIdentifiers: any[];
  mfa: any;

  editMode: boolean;

  enroll: Enroll = {} as any;
  challenge: Challenge = {} as any;
  rememberDevice: RememberDevice = {} as any;
  mfaStepUpRule = '';

  formChanged = false;

  private static getDefaultRiskAssessment(): any {
    return {
      enabled: false,
      deviceAssessment: { enabled: false },
      ipReputationAssessment: { enabled: false },
      geoVelocityAssessment: { enabled: false },
    };
  }
  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private applicationService: ApplicationService,
    private factorService: FactorService,
    private authService: AuthService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = this.route.snapshot.data['application'];
    this.deviceIdentifiers = this.route.snapshot.data['deviceIdentifiers'] || [];
    this.mfa = this.application.settings == null ? {} : this.application.settings.mfa || {};
    this.mfa.enroll = {
      ...(this.mfa.enroll
        ? this.mfa.enroll
        : {
            active: false,
            forceEnrollment: false,
            skipTimeSeconds: null,
            type: 'REQUIRED',
          }),
    };
    this.mfaStepUpRule = this.mfa.stepUpAuthenticationRule ? this.mfa.stepUpAuthenticationRule.slice() : '';
    this.rememberDevice = { ...this.mfa.rememberDevice };
    this.enroll = { ...this.mfa.enroll };
    this.challenge = {
      ...(this.mfa.challenge
        ? this.mfa.challenge
        : {
            active: false,
            challengeRule: '',
            type: 'REQUIRED',
          }),
      riskAssessment: this.application.settings.riskAssessment ? { ...this.application.settings.riskAssessment } : undefined,
      adaptiveMfaRule: this.mfa.adaptiveAuthenticationRule ? this.mfa.adaptiveAuthenticationRule.slice() : '',
    };
    this.application.settings.riskAssessment =
      this.application.settings.riskAssessment || ApplicationFactorsComponent.getDefaultRiskAssessment();
    this.editMode = this.authService.hasPermissions(['application_settings_update']);
    this.factorService.findByDomain(this.domainId).subscribe((response) => (this.factors = [...response]));
  }

  patch(): void {
    const data = {
      factors: this.application.factors,
      settings: {
        riskAssessment: this.challenge.riskAssessment,
        mfa: {
          stepUpAuthenticationRule: this.mfaStepUpRule,
          stepUpAuthentication: {
            active: this.mfaStepUpRule !== '',
            stepUpAuthenticationRule: this.mfaStepUpRule,
          },
          adaptiveAuthenticationRule: this.challenge.adaptiveMfaRule,
          rememberDevice: this.rememberDevice,
          enrollment: {
            forceEnrollment: this.enroll.forceEnrollment,
            skipTimeSeconds: this.enroll.skipTimeSeconds,
          },
          enroll: this.enroll,
          challenge: {
            active: this.challenge.active,
            challengeRule: this.challenge.challengeRule,
            type: this.challenge.type,
          },
        },
      },
    };
    this.applicationService.patch(this.domainId, this.application.id, data).subscribe((response) => {
      this.application = response;
      this.formChanged = false;
      this.mfa = this.application.settings == null ? {} : this.application.settings.mfa || {};
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
    });
  }

  selectFactor(selectFactorEvent: any): void {
    if (selectFactorEvent.checked) {
      this.application.factors = this.application.factors || [];
      this.application.factors.push(selectFactorEvent.factorId);
    } else {
      this.application.factors.splice(this.application.factors.indexOf(selectFactorEvent.factorId), 1);
    }
    this.formChanged = true;
  }

  updateRememberDevice(rememberDevice: RememberDevice): void {
    this.rememberDevice = { ...rememberDevice };
    if (!this.rememberDevice.deviceIdentifierId) {
      this.rememberDevice.deviceIdentifierId = this.deviceIdentifiers[0].id;
    }
    this.formChanged = true;
  }

  updateEnrollment(enroll: Enroll): void {
    this.enroll = {
      ...enroll,
      enrollmentRule: (enroll.enrollmentRule || '').slice(),
      // skipEnrollmentRule: (options.enrollmentRule || '').slice(),
    };
    this.formChanged = true;
  }

  updateChallenge(challenge: Challenge): void {
    this.challenge = {
      ...challenge,
      challengeRule: (challenge.challengeRule || '').slice(),
      riskAssessment: challenge.riskAssessment ? { ...challenge.riskAssessment } : undefined,
    };
    this.formChanged = true;
  }

  updateStepUpRule(stepUpRule: string): void {
    this.mfaStepUpRule = stepUpRule.slice();
    this.formChanged = true;
  }

  hasFactors(): boolean {
    return this.factors && this.factors.length > 0;
  }

  hasSelectedFactors(): boolean {
    return this.application.factors && this.application.factors.length > 0;
  }
}

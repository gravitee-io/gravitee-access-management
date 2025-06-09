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
import { map } from 'rxjs/operators';

import { ApplicationService } from '../../../../../services/application.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { FactorService } from '../../../../../services/factor.service';
import { AuthService } from '../../../../../services/auth.service';

import { Challenge, Enroll, MfaFactor, RememberDevice, StepUpAuth } from './model';

@Component({
  selector: 'app-application-factors',
  templateUrl: './factors.component.html',
  styleUrls: ['./factors.component.scss'],
  standalone: false,
})
export class ApplicationFactorsComponent implements OnInit {
  private domainId: string;

  application: any;
  factors: MfaFactor[];
  deviceIdentifiers: any[];
  mfa: any;

  editMode: boolean;

  enroll: Enroll = {} as any;
  challenge: Challenge = {} as any;
  rememberDevice: RememberDevice = {} as any;
  stepUpAuth: StepUpAuth = {} as any;

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
    this.stepUpAuth = { ...this.mfa.stepUpAuthentication };
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
    this.factorService
      .findByDomain(this.domainId)
      .pipe(
        map((factors) =>
          factors.map((f: any) => {
            const appFactor = this.mfa.factor?.applicationFactors?.filter((mf: MfaFactor) => mf.id === f.id)?.[0];
            const defaultId = this.mfa.factor?.defaultFactorId ? this.mfa.factor.defaultFactorId : factors[0]?.id;
            return {
              ...f,
              selectionRule: appFactor?.selectionRule,
              selected: appFactor !== undefined,
              isDefault: defaultId === f.id,
            };
          }),
        ),
      )
      .subscribe((response) => (this.factors = [...response]));
  }

  patch(): void {
    const factorsApp = this.factors
      .filter((i) => i.selected)
      .map((i) => {
        return {
          id: i.id,
          selectionRule: i.selectionRule,
        } as any;
      });
    const defaultFactors = this.factors
      .filter((i) => i.selected)
      .filter((i) => i.isDefault)
      .map((i) => i.id);
    const data = {
      factors: this.application.factors,
      settings: {
        riskAssessment: this.challenge.riskAssessment,
        mfa: {
          factor: {
            defaultFactorId: defaultFactors.length > 0 ? defaultFactors[0] : undefined,
            applicationFactors: factorsApp,
          },
          stepUpAuthenticationRule: this.stepUpAuth.active ? this.stepUpAuth.stepUpAuthenticationRule : '',
          stepUpAuthentication: this.stepUpAuth,
          adaptiveAuthenticationRule: this.challenge.adaptiveMfaRule,
          rememberDevice: this.rememberDevice,
          enrollment: {
            forceEnrollment: this.enroll.forceEnrollment,
            skipTimeSeconds: this.enroll.skipTimeSeconds,
          },
          enroll: {
            active: factorsApp.length > 0 ? this.enroll.active : false,
            enrollmentRule: this.enroll.enrollmentRule,
            enrollmentSkipActive: this.enroll.enrollmentSkipActive,
            enrollmentSkipRule: this.enroll.enrollmentSkipRule,
            forceEnrollment: this.enroll.forceEnrollment,
            skipTimeSeconds: this.enroll.skipTimeSeconds,
            type: this.enroll.type,
          },
          challenge: {
            active: factorsApp.length > 0 ? this.challenge.active : false,
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

  updateFactors(updatedFactors: MfaFactor[]): void {
    this.factors = [...updatedFactors];
    this.application.factors = this.factors.filter((factor) => factor.selected).map((factor) => factor.id);
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
      enrollmentSkipRule: (enroll.enrollmentSkipRule || '').slice(),
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

  updateStepUpRule(stepUpRule: StepUpAuth): void {
    this.stepUpAuth = stepUpRule;
    this.formChanged = true;
  }

  hasFactors(): boolean {
    return this.factors && this.factors.length > 0;
  }

  hasSelectedFactors(): boolean {
    return this.application.factors && this.application.factors.length > 0;
  }
}

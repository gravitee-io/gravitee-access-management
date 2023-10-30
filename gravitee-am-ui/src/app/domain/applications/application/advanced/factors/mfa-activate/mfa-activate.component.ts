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
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { takeUntil, tap } from 'rxjs/operators';
import { Observable, Subject } from 'rxjs';
import { GioLicenseService, LicenseOptions } from '@gravitee/ui-particles-angular';

import { OrganizationService } from '../../../../../../services/organization.service';
import { AmFeature } from '../../../../../../components/gio-license/gio-license-data';

interface ModeOption {
  label: string;
  message: string;
  licenseOptions?: LicenseOptions;
  isMissingFeature$?: Observable<boolean>;
  warning?: string;
}

@Component({
  selector: 'mfa-activate',
  templateUrl: './mfa-activate.component.html',
  styleUrls: ['./mfa-activate.component.scss'],
})
export class MfaActivateComponent implements OnInit, OnDestroy {
  constructor(private organizationService: OrganizationService, private licenseService: GioLicenseService) {}

  @Input() enrollment: any;
  @Input() adaptiveMfaRule: string;
  @Input() riskAssessment: any;
  @Output('settings-change') settingsChangeEmitter: EventEmitter<any> = new EventEmitter<any>();
  currentMode: any;
  factors: any[];
  modes: any[];

  private unsubscribe$: Subject<boolean> = new Subject<boolean>();

  private static modeOptions: Record<string, ModeOption> = {
    OPTIONAL: {
      label: 'Optional',
      message: 'Choose the period of time users can skip MFA. Default time is 10 hours.',
    },
    REQUIRED: {
      label: 'Required',
      message: 'MFA will always be displayed.',
    },
    CONDITIONAL: {
      label: 'Conditional',
      message: 'Set conditions that will display MFA based on the user’s information',
      warning: 'You need to install the <b> GeoIP service </b> plugin to use the geoip based variables',
    },
    INTELLIGENT: {
      label: 'Risk-based',
      message: 'Configure the thresholds that will display MFA based on risks.',
      warning: 'You need to install the <b> GeoIP service </b> and <b> Risk Assessment </b> plugins to use Risk-based MFA',
      licenseOptions: {
        feature: AmFeature.AM_GRAVITEE_RISK_ASSESSMENT,
      },
    },
  };

  ngOnInit(): void {
    this.organizationService
      .factors()
      .pipe(
        tap((factors) => {
          this.factors = factors;
        }),
        takeUntil(this.unsubscribe$),
      )
      .subscribe();

    this.initModes();

    if (this.riskAssessment && this.riskAssessment.enabled) {
      this.currentMode = MfaActivateComponent.modeOptions.INTELLIGENT;
    } else if (this.adaptiveMfaRule && this.adaptiveMfaRule !== '') {
      this.currentMode = MfaActivateComponent.modeOptions.CONDITIONAL;
    } else if (this.enrollment && this.enrollment.forceEnrollment) {
      this.currentMode = MfaActivateComponent.modeOptions.REQUIRED;
    } else {
      this.currentMode = MfaActivateComponent.modeOptions.OPTIONAL;
    }
  }

  ngOnDestroy(): void {
    this.unsubscribe$.next(false);
    this.unsubscribe$.unsubscribe();
  }

  get modeOptions() {
    return MfaActivateComponent.modeOptions;
  }

  applyModeChange($event) {
    switch ($event.value) {
      case MfaActivateComponent.modeOptions.OPTIONAL:
        this.updateOptional({ forceEnrollment: false, skipTimeSeconds: this.enrollment.skipTimeSeconds });
        break;
      case MfaActivateComponent.modeOptions.REQUIRED:
        this.updateRequired(this.enrollment);
        break;
      case MfaActivateComponent.modeOptions.CONDITIONAL:
        this.updateAdaptiveMfaRule(this.adaptiveMfaRule);
        break;
      case MfaActivateComponent.modeOptions.INTELLIGENT:
        this.updateRiskAssessment(this.riskAssessment);
        break;
    }
  }

  updateOptional(enrollment) {
    this.settingsChangeEmitter.emit({
      enrollment: enrollment,
      adaptiveMfaRule: '',
      riskAssessment: this.getRiskAssessment(this.riskAssessment, false),
      selectedMFAOption: MfaActivateComponent.modeOptions.OPTIONAL.label,
    });
  }

  updateRequired(enrollment) {
    this.settingsChangeEmitter.emit({
      enrollment: { forceEnrollment: true, skipTimeSeconds: enrollment.skipTimeSeconds },
      adaptiveMfaRule: '',
      riskAssessment: this.getRiskAssessment(this.riskAssessment, false),
      selectedMFAOption: MfaActivateComponent.modeOptions.REQUIRED.label,
    });
  }

  updateAdaptiveMfaRule(adaptiveMfaRule) {
    this.settingsChangeEmitter.emit({
      enrollment: { forceEnrollment: true, skipTimeSeconds: this.enrollment.skipTimeSeconds },
      adaptiveMfaRule: adaptiveMfaRule,
      riskAssessment: this.getRiskAssessment(this.riskAssessment, false),
      selectedMFAOption: MfaActivateComponent.modeOptions.CONDITIONAL.label,
    });
  }

  updateRiskAssessment(assessments) {
    const safeRiskAssessment = this.getRiskAssessment(assessments, true);
    const value = {
      enrollment: { forceEnrollment: true, skipTimeSeconds: this.enrollment.skipTimeSeconds },
      adaptiveMfaRule: MfaActivateComponent.computeRiskAssessmentRule(safeRiskAssessment),
      riskAssessment: safeRiskAssessment,
      selectedMFAOption: MfaActivateComponent.modeOptions.INTELLIGENT.label,
    };
    this.settingsChangeEmitter.emit(value);
  }

  private static readonly RISK_ASSESSMENT_PREFIX = "#context.attributes['risk_assessment'].";
  private static readonly RISK_ASSESSMENT_SUFFIX = ".assessment.name() == 'SAFE'";

  private static computeRiskAssessmentRule(riskAssessment) {
    let rule = '{';
    const devices = riskAssessment.deviceAssessment;
    if (devices && devices.enabled) {
      rule += this.RISK_ASSESSMENT_PREFIX + 'devices' + this.RISK_ASSESSMENT_SUFFIX;
    }
    const ipReputation = riskAssessment.ipReputationAssessment;
    if (ipReputation && ipReputation.enabled) {
      rule += (rule.length === 1 ? ' ' : ' && ') + this.RISK_ASSESSMENT_PREFIX + 'ipReputation' + this.RISK_ASSESSMENT_SUFFIX;
    }
    const geoVelocity = riskAssessment.geoVelocityAssessment;
    if (geoVelocity && geoVelocity.enabled) {
      rule += (rule.length === 1 ? ' ' : ' && ') + this.RISK_ASSESSMENT_PREFIX + 'geoVelocity' + this.RISK_ASSESSMENT_SUFFIX;
    }
    rule += '}';
    return rule;
  }

  private getRiskAssessment(riskAssessment, enabled) {
    return {
      enabled: enabled,
      deviceAssessment: this.getAssessment(riskAssessment, 'deviceAssessment'),
      ipReputationAssessment: this.getAssessment(riskAssessment, 'ipReputationAssessment'),
      geoVelocityAssessment: this.getAssessment(riskAssessment, 'geoVelocityAssessment'),
    };
  }

  private getAssessment(riskAssessment, assessmentName: string) {
    return riskAssessment && riskAssessment[assessmentName] ? riskAssessment[assessmentName] : { enabled: false, thresholds: {} };
  }

  private initModes() {
    this.modes = Object.keys(MfaActivateComponent.modeOptions).map((key) => {
      const option = MfaActivateComponent.modeOptions[key];
      option.isMissingFeature$ = this.licenseService.isMissingFeature$(option.licenseOptions);
      return option;
    });
  }
}

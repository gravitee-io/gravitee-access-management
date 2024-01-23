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
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { GioLicenseService, LicenseOptions } from '@gravitee/ui-particles-angular';
import { Observable } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';

import { AmFeature } from '../../../../../../components/gio-license/gio-license-data';
import { ExpressionInfoDialog } from '../expression-info-dialog/expression-info-dialog.component';

interface ModeOption {
  label: string;
  message: string;
  licenseOptions?: LicenseOptions;
  isMissingFeature$?: Observable<boolean>;
  warning?: string;
}

@Component({
  selector: 'mfa-challenge',
  templateUrl: './mfa-challenge.component.html',
  styleUrls: ['./mfa-challenge.component.scss'],
})
export class MfaChallengeComponent implements OnInit {
  private static readonly RISK_ASSESSMENT_PREFIX: string = "#context.attributes['risk_assessment'].";
  private static readonly RISK_ASSESSMENT_SUFFIX: string = ".assessment.name() == 'SAFE'";
  private static readonly EXPRESSION_INFO: string = `{#request.params['scope'][0] == 'write'}
{#request.params['audience'][0] == 'https://myapi.com'}
{#context.attributes['geoip']['country_iso_code'] == 'US'}
{#context.attributes['geoip']['country_name'] == 'United States'}
{#context.attributes['geoip']['continent_name'] == 'North America'}
{#context.attributes['geoip']['region_name'] == 'Washington'}`;
  private static modeOptions: Record<string, ModeOption> = {
    INTELLIGENT: {
      label: 'Risk-based',
      message: 'Configure how users will be challenged based on specific risks.',
      warning: 'You need to install the <b> GeoIP service </b> and <b> Risk Assessment </b> plugins to use Risk-based MFA',
      licenseOptions: {
        feature: AmFeature.AM_GRAVITEE_RISK_ASSESSMENT,
      },
    },
    REQUIRED: {
      label: 'Required',
      message: 'MFA challenges will always be displayed and required during sign-in.',
    },
    CONDITIONAL: {
      label: 'Conditional',
      message: 'Use Gravitee Expression Language to configure MFA challenges based on specific rules.',
      warning: 'You need to install the <b> GeoIP service </b> plugin to use the geoip based variables',
    },
  };

  @Output() settingsChange: EventEmitter<any> = new EventEmitter<any>();
  @Input() enrollment: any;
  @Input() riskAssessment: any;

  enable = false;
  currentMode: any;
  modes: any[];
  conditionalRules: string;

  constructor(private licenseService: GioLicenseService, private dialog: MatDialog) {}

  ngOnInit(): void {
    this.initModes();
    this.currentMode = MfaChallengeComponent.modeOptions.INTELLIGENT;
  }

  openInfoDialog($event: any): void {
    $event.preventDefault();
    this.dialog.open(ExpressionInfoDialog, {
      width: '700px',
      data: {
        info: MfaChallengeComponent.EXPRESSION_INFO,
      },
    });
  }

  get modeOptions(): Record<string, ModeOption> {
    return MfaChallengeComponent.modeOptions;
  }

  switchEnable(): void {
    this.enable = !this.enable;
    this.settingsChange.emit({
      riskAssessment: this.riskAssessment,
      selectedChallengeMFAOption: this.currentMode?.label,
      active: this.enable,
    });
  }

  selectChallengeOption(option: ModeOption): void {
    this.currentMode = option;
    this.riskAssessment.enabled = option === MfaChallengeComponent.modeOptions.INTELLIGENT;
    this.settingsChange.emit({
      riskAssessment: this.riskAssessment,
      selectedChallengeMFAOption: option.label,
      active: true,
    });
  }

  isChallengeChecked(option: ModeOption): boolean {
    return this.currentMode === option;
  }

  updateRiskAssessment(assessments: any): void {
    const safeRiskAssessment = this.getRiskAssessment(assessments, true);
    const value = {
      adaptiveMfaRule: this.computeRiskAssessmentRule(safeRiskAssessment),
      riskAssessment: safeRiskAssessment,
      selectedChallengeMFAOption: MfaChallengeComponent.modeOptions.INTELLIGENT.label,
      active: true,
    };
    this.settingsChange.emit(value);
  }

  updateConditional($event: any): void {
    this.conditionalRules = $event.target.value;
    console.log(' update cond ', this.conditionalRules);
    const value = {
      mfaChallengeConditionalRules: this.conditionalRules,
      selectedChallengeMFAOption: MfaChallengeComponent.modeOptions.CONDITIONAL.label,
    };
    this.settingsChange.emit(value);
  }

  private getAssessment(riskAssessment: any, assessmentName: string): any {
    return riskAssessment?.[assessmentName] ? riskAssessment[assessmentName] : { enabled: false, thresholds: {} };
  }

  private getRiskAssessment(riskAssessment: any, enabled: any): any {
    return {
      enabled: enabled,
      deviceAssessment: this.getAssessment(riskAssessment, 'deviceAssessment'),
      ipReputationAssessment: this.getAssessment(riskAssessment, 'ipReputationAssessment'),
      geoVelocityAssessment: this.getAssessment(riskAssessment, 'geoVelocityAssessment'),
    };
  }

  private computeRiskAssessmentRule(riskAssessment: any): string {
    let rule = '{';
    const devices = riskAssessment.deviceAssessment;
    if (devices?.enabled) {
      rule += MfaChallengeComponent.RISK_ASSESSMENT_PREFIX + 'devices' + MfaChallengeComponent.RISK_ASSESSMENT_SUFFIX;
    }
    const ipReputation = riskAssessment.ipReputationAssessment;
    if (ipReputation?.enabled) {
      rule +=
        (rule.length === 1 ? ' ' : ' && ') +
        MfaChallengeComponent.RISK_ASSESSMENT_PREFIX +
        'ipReputation' +
        MfaChallengeComponent.RISK_ASSESSMENT_SUFFIX;
    }
    const geoVelocity = riskAssessment.geoVelocityAssessment;
    if (geoVelocity?.enabled) {
      rule +=
        (rule.length === 1 ? ' ' : ' && ') +
        MfaChallengeComponent.RISK_ASSESSMENT_PREFIX +
        'geoVelocity' +
        MfaChallengeComponent.RISK_ASSESSMENT_SUFFIX;
    }
    rule += '}';
    return rule;
  }

  private initModes(): void {
    this.modes = Object.keys(MfaChallengeComponent.modeOptions).map((key) => {
      const option = MfaChallengeComponent.modeOptions[key];
      option.isMissingFeature$ = this.licenseService.isMissingFeature$(option.licenseOptions);
      return option;
    });
  }
}

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
import { GioLicenseService } from '@gravitee/ui-particles-angular';
import { MatDialog } from '@angular/material/dialog';

import { AmFeature } from '../../../../../../components/gio-license/gio-license-data';
import { ExpressionInfoDialogComponent } from '../expression-info-dialog/expression-info-dialog.component';
import { Challenge, ModeOption } from '../model';

@Component({
  selector: 'mfa-challenge',
  templateUrl: './mfa-challenge.component.html',
  styleUrls: ['./mfa-challenge.component.scss'],
  standalone: false,
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
    RISK_BASED: {
      label: 'Risk-based',
      value: 'RISK_BASED',
      message: 'Configure how users will be challenged based on specific risks.',
      warning: 'You need to install the GeoIP service and Risk Assessment plugins to use Risk-based MFA',
      licenseOptions: {
        feature: AmFeature.AM_GRAVITEE_RISK_ASSESSMENT,
      },
    },
    REQUIRED: {
      label: 'Required',
      value: 'REQUIRED',
      message: 'MFA challenges will always be displayed and required during sign-in.',
    },
    CONDITIONAL: {
      label: 'Conditional',
      value: 'CONDITIONAL',
      message: 'Use Gravitee Expression Language to configure MFA challenges based on specific rules.',
      warning: 'You need to install the GeoIP service plugin to use the geoip based variables',
      warningLink: 'https://docs.gravitee.io/am/current/am_userguide_mfa_amfa.html',
    },
  };
  @Input() challenge: Challenge;
  @Input() riskAssessment: any;
  @Output() settingsChange = new EventEmitter<Challenge>();

  currentMode: any;
  modes: any[];
  adaptiveMfaRule: string;
  assessments: any;

  constructor(
    private licenseService: GioLicenseService,
    private dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.initModes();
    this.currentMode = this.challenge.type
      ? MfaChallengeComponent.modeOptions[this.challenge.type.toUpperCase()]
      : MfaChallengeComponent.modeOptions.OPTIONAL;
  }

  openInfoDialog($event: any): void {
    $event.preventDefault();
    this.dialog.open(ExpressionInfoDialogComponent, {
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
    this.challenge.active = !this.challenge.active;
    this.update();
  }

  selectChallengeOption(option: ModeOption): void {
    this.currentMode = option;
    this.update();
  }

  isChallengeChecked(option: ModeOption): boolean {
    return this.currentMode === option;
  }

  updateRiskAssessment(assessments: any): void {
    this.assessments = assessments;
    this.update();
  }

  updateConditional($event: any): void {
    this.challenge.challengeRule = $event.target.value;
    this.update();
  }

  private getAssessment(riskAssessment: any, assessmentName: string): any {
    return riskAssessment?.[assessmentName] ? riskAssessment[assessmentName] : { enabled: false, thresholds: {} };
  }

  private getRiskAssessment(riskAssessment: any, enabled: boolean): any {
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
      option.isMissingFeature$ = this.licenseService.isMissingFeature$(option.licenseOptions?.feature);
      return option;
    });
  }

  private update(): void {
    const isRiskBased = this.currentMode === MfaChallengeComponent.modeOptions.RISK_BASED;
    this.riskAssessment = this.getRiskAssessment(this.assessments, isRiskBased);
    if (isRiskBased) {
      this.challenge.challengeRule = this.computeRiskAssessmentRule(this.riskAssessment);
      this.adaptiveMfaRule = this.challenge.challengeRule;
    } else if (this.currentMode === MfaChallengeComponent.modeOptions.CONDITIONAL) {
      this.adaptiveMfaRule = this.challenge.challengeRule;
    } else {
      this.adaptiveMfaRule = '';
      this.challenge.challengeRule = '';
    }
    this.settingsChange.emit({
      active: this.challenge.active,
      adaptiveMfaRule: this.adaptiveMfaRule,
      riskAssessment: this.riskAssessment,
      challengeRule: this.challenge.challengeRule,
      type: this.currentMode.value,
    });
  }
}

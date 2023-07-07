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
import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {OrganizationService} from '../../../../../../services/organization.service';

@Component({
  selector: 'mfa-activate',
  templateUrl: './mfa-activate.component.html',
  styleUrls: ['./mfa-activate.component.scss']
})
export class MfaActivateComponent implements OnInit {

  constructor(private organizationService: OrganizationService) {
  }

  @Input() enrollment: any;
  @Input() adaptiveMfaRule: string;
  @Input() riskAssessment: any;
  @Output('settings-change') settingsChangeEmitter: EventEmitter<any> = new EventEmitter<any>();
  currentMode: any;
  factors: any[];

  private static modeOptions = {
    OPTIONAL: {
      'label': 'Optional',
      'message': 'Choose the period of time users can skip MFA. Default time is 10 hours.',
      'ee': false,
    },
    REQUIRED: {
      'label': 'Required',
      'message': 'MFA will always be displayed.',
      'ee': false,
    },
    CONDITIONAL: {
      'label': 'Conditional',
      'message': 'Set conditions that will display MFA based on the user’s information',
      'warning': 'You need to install the <b> GeoIP service </b> plugin to use the geoip based variables',
      'ee': false,
    },
    INTELLIGENT: {
      'label': 'Risk-based',
      'message': 'Configure the thresholds that will display MFA based on risks.',
      'warning': 'You need to install the <b> GeoIP service </b> and <b> Risk Assessment </b> plugins to use Risk-based MFA',
      'ee': true,
    },
  };

  private static modes = Object.keys(MfaActivateComponent.modeOptions)
    .map(key => MfaActivateComponent.modeOptions[key])

  ngOnInit(): void {
    this.organizationService.factors().subscribe(data => {
      this.factors = data
    });

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

  get modes() {
    return MfaActivateComponent.modes;
  }

  get modeOptions() {
    return MfaActivateComponent.modeOptions;
  }

  applyModeChange($event) {
    switch ($event.value) {
      case MfaActivateComponent.modeOptions.OPTIONAL:
        this.updateOptional({'forceEnrollment': false, 'skipTimeSeconds': this.enrollment.skipTimeSeconds});
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
      'enrollment': enrollment,
      'adaptiveMfaRule': '',
      'riskAssessment': this.getRiskAssessment(this.riskAssessment, false)
    })
  }

  updateRequired(enrollment) {
    this.settingsChangeEmitter.emit({
      'enrollment': {'forceEnrollment': true, 'skipTimeSeconds': enrollment.skipTimeSeconds},
      'adaptiveMfaRule': '',
      'riskAssessment': this.getRiskAssessment(this.riskAssessment, false)
    })
  }

  updateAdaptiveMfaRule(adaptiveMfaRule) {
    this.settingsChangeEmitter.emit({
        'enrollment': {'forceEnrollment': true, 'skipTimeSeconds': this.enrollment.skipTimeSeconds},
        'adaptiveMfaRule': adaptiveMfaRule,
        'riskAssessment': this.getRiskAssessment(this.riskAssessment, false)
      }
    )
  }

  updateRiskAssessment(assessments) {
    const safeRiskAssessment = this.getRiskAssessment(assessments, true);
    const value = {
      'enrollment': {'forceEnrollment': true, 'skipTimeSeconds': this.enrollment.skipTimeSeconds},
      'adaptiveMfaRule': MfaActivateComponent.computeRiskAssessmentRule(safeRiskAssessment),
      'riskAssessment': safeRiskAssessment
    };
    this.settingsChangeEmitter.emit(value)
  }

  private static readonly RISK_ASSESSMENT_PREFIX = '#context.attributes[\'risk_assessment\'].';
  private static readonly RISK_ASSESSMENT_SUFFIX = '.assessment.name() == \'SAFE\'';

  private static computeRiskAssessmentRule(riskAssessment) {
    let rule = '{';
    const devices = riskAssessment.deviceAssessment;
    if (devices && devices.enabled) {
      rule += this.RISK_ASSESSMENT_PREFIX + 'devices' + this.RISK_ASSESSMENT_SUFFIX
    }
    const ipReputation = riskAssessment.ipReputationAssessment;
    if (ipReputation && ipReputation.enabled) {
      rule += (rule.length == 1 ? ' ' : ' && ') + this.RISK_ASSESSMENT_PREFIX + 'ipReputation' + this.RISK_ASSESSMENT_SUFFIX
    }
    const geoVelocity = riskAssessment.geoVelocityAssessment;
    if (geoVelocity && geoVelocity.enabled) {
      rule += (rule.length == 1 ? ' ' : ' && ') + this.RISK_ASSESSMENT_PREFIX + 'geoVelocity' + this.RISK_ASSESSMENT_SUFFIX
    }
    rule += '}';
    return rule
  }

  private getRiskAssessment(riskAssessment, enabled) {
    return {
      'enabled': enabled,
      'deviceAssessment': this.getAssessment(riskAssessment, 'deviceAssessment'),
      'ipReputationAssessment': this.getAssessment(riskAssessment, 'ipReputationAssessment'),
      'geoVelocityAssessment': this.getAssessment(riskAssessment, 'geoVelocityAssessment'),
    };
  }

  private getAssessment(riskAssessment, assessmentName: string) {
    return riskAssessment && riskAssessment[assessmentName] ?
      riskAssessment[assessmentName] :
      {'enabled': false, thresholds: {}};
  }

  modeLicenseMetadata(mode): any {
    const response = {'deployed': true, 'feature': ''}
    if (mode.label === 'Risk-based') {
      response.deployed = false;
      response.feature = 'gravitee-risk-assessment';

      if (this.factors != null) {
        const factor = this.factors['gravitee-risk-assessment'] ?? this.factors['risk-assessment'];
        if (factor != null) {
          response.deployed = factor.deployed;
          response.feature = factor.feature;
        }
      }
    }

    return response;
  }
}

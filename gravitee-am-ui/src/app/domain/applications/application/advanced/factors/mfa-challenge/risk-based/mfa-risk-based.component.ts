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

@Component({
  selector: 'mfa-intelligent',
  templateUrl: './mfa-risk-based.component.html',
  styleUrls: ['./mfa-risk-based.component.scss'],
  standalone: false,
})
export class MfaRiskBasedComponent implements OnInit {
  /* Arbitrary score */
  private static devicesOptions = {
    DISABLED: { label: 'Disabled', value: null, threshold: NaN, tooltip: 'Disabled' },
    UNKNOWN: { label: 'Unknown', value: 'HIGH', threshold: 1.0, tooltip: 'No device found' },
  };

  /* Values are in % */
  private static ipReputationOptions = {
    DISABLED: { label: 'Disabled', value: null, threshold: NaN, tooltip: 'Disabled' },
    LOW: { label: 'Low', value: 'LOW', threshold: 1.0, tooltip: '1% and more' },
    MEDIUM: { label: 'Medium', value: 'MEDIUM', threshold: 30.0, tooltip: '30% and more' },
    HIGH: { label: 'High', value: 'HIGH', threshold: 70.0, tooltip: 'between 70% and 100%' },
  };

  /* Values are in m/s */
  private static geoVelocityOptions = {
    DISABLED: { label: 'Disabled', value: null, threshold: NaN, tooltip: 'Disabled' },
    LOW: { label: 'Low', value: 'LOW', threshold: 5.0 / 18.0, tooltip: '1km/h and more' } /* 1 km/h */,
    MEDIUM: { label: 'Medium', value: 'MEDIUM', threshold: 125.0 / 18, tooltip: '25km/h and more' } /* 25 km/h */,
    HIGH: { label: 'High', value: 'HIGH', threshold: 625.0 / 9.0, tooltip: '250 km/h and more' } /* 250 km/h */,
  };

  @Input() riskAssessment: any = {};
  @Output() settingsChange: EventEmitter<any> = new EventEmitter<any>();

  currentDeviceAssessment: any;
  currentIpReputationAssessment: any;
  currentGeoVelocityAssessment: any;

  private static buildAssessment(assessment: any, option: any) {
    if (option !== assessment) {
      const thresholds = {};
      thresholds[assessment.value] = assessment.threshold;
      return { enabled: true, thresholds: thresholds };
    }
    return this.disabledAssessment();
  }

  private static disabledAssessment() {
    return { enabled: false, thresholds: {} };
  }
  ngOnInit(): void {
    this.currentDeviceAssessment = this.riskAssessment.deviceAssessment || MfaRiskBasedComponent.disabledAssessment();
    this.currentIpReputationAssessment = this.riskAssessment.ipReputationAssessment || MfaRiskBasedComponent.disabledAssessment();
    this.currentGeoVelocityAssessment = this.riskAssessment.geoVelocityAssessment || MfaRiskBasedComponent.disabledAssessment();
  }

  get deviceAssessments() {
    return Object.values(MfaRiskBasedComponent.devicesOptions);
  }

  get ipReputationAssessments() {
    return Object.values(MfaRiskBasedComponent.ipReputationOptions);
  }

  get geoVelocityAssessments() {
    return Object.values(MfaRiskBasedComponent.geoVelocityOptions);
  }

  deviceAssessmentChange(assessment: any): void {
    this.currentDeviceAssessment = MfaRiskBasedComponent.buildAssessment(assessment, MfaRiskBasedComponent.devicesOptions.DISABLED);
    this.update();
  }

  ipReputationAssessmentChange(assessment: any): void {
    this.currentIpReputationAssessment = MfaRiskBasedComponent.buildAssessment(
      assessment,
      MfaRiskBasedComponent.ipReputationOptions.DISABLED,
    );
    this.update();
  }

  geoVelocityAssessmentChange(assessment: any): void {
    this.currentGeoVelocityAssessment = MfaRiskBasedComponent.buildAssessment(
      assessment,
      MfaRiskBasedComponent.geoVelocityOptions.DISABLED,
    );
    this.update();
  }

  private update(): void {
    this.settingsChange.emit({
      deviceAssessment: this.currentDeviceAssessment,
      ipReputationAssessment: this.currentIpReputationAssessment,
      geoVelocityAssessment: this.currentGeoVelocityAssessment,
    });
  }
}

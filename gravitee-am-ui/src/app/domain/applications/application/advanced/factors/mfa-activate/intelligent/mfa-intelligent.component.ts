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
import {Component, EventEmitter, Input, OnInit, Output} from "@angular/core";

@Component({
  selector: 'mfa-intelligent',
  templateUrl: './mfa-intelligent.component.html',
  styleUrls: ['./mfa-intelligent.component.scss']
})
export class MfaIntelligentComponent implements OnInit {

  @Input() riskAssessment: any = {};
  @Output("on-settings-change") settingsChangeEmitter: EventEmitter<any> = new EventEmitter<any>();

  currentDeviceAssessment: any;
  currentIpReputationAssessment: any;
  currentGeoVelocityAssessment: any;

  ngOnInit(): void {
    this.currentDeviceAssessment = this.riskAssessment.deviceAssessment || MfaIntelligentComponent.disabledAssessment();
    this.currentIpReputationAssessment = this.riskAssessment.ipReputationAssessment || MfaIntelligentComponent.disabledAssessment();
    this.currentGeoVelocityAssessment = this.riskAssessment.geoVelocityAssessment || MfaIntelligentComponent.disabledAssessment();
  }

  /* Arbitrary score */
  private static devicesOptions = {
    DISABLED: {"label": "Disabled", "value": null, "threshold": NaN, "tooltip" : "Disabled"},
    UNKNOWN: {"label": "Unknown", "value": "HIGH", "threshold": 1.0, "tooltip" : "No device found"},
  };


  get deviceAssessments() {
    return Object.values(MfaIntelligentComponent.devicesOptions);
  }

  /* Values are in % */
  private static ipReputationOptions = {
    DISABLED: {"label": "Disabled", "value": null, "threshold": NaN, "tooltip" : "Disabled"},
    LOW: {"label": "Low", "value": "LOW", "threshold": 1.0, "tooltip": "1% and more"},
    MEDIUM: {"label": "Medium", "value": "MEDIUM", "threshold": 30.0, "tooltip": "30% and more"},
    HIGH: {"label": "High", "value": "HIGH", "threshold": 70.0, "tooltip": "between 70% and 100%"}
  };

  get ipReputationAssessments() {
    return Object.values(MfaIntelligentComponent.ipReputationOptions);
  }

  /* Values are in m/s */
  private static geoVelocityOptions = {
    DISABLED: {"label": "Disabled", "value": null, "threshold": NaN, "tooltip" : "Disabled"},
    LOW: {"label": "Low", "value": "LOW", "threshold": (5.0 / 18.0), "tooltip": "1km/h and more"}, /* 1 km/h */
    MEDIUM: {"label": "Medium", "value": "MEDIUM", "threshold": (125.0 / 18), "tooltip": "25km/h and more"},  /* 25 km/h */
    HIGH: {"label": "High", "value": "HIGH", "threshold": (625.0 / 9.0), "tooltip": "250 km/h and more"} /* 250 km/h */
  };

  get geoVelocityAssessments() {
    return Object.values(MfaIntelligentComponent.geoVelocityOptions);
  }

  deviceAssessmentChange(assessment) {
    this.currentDeviceAssessment = MfaIntelligentComponent.buildAssessment(
      assessment,
      MfaIntelligentComponent.devicesOptions.DISABLED);
    this.emitRiskAssessmentChange();
  }

  ipReputationAssessmentChange(assessment) {
    this.currentIpReputationAssessment = MfaIntelligentComponent.buildAssessment(
      assessment,
      MfaIntelligentComponent.ipReputationOptions.DISABLED);
    this.emitRiskAssessmentChange();
  }

  geoVelocityAssessmentChange(assessment) {
    this.currentGeoVelocityAssessment = MfaIntelligentComponent.buildAssessment(
      assessment,
      MfaIntelligentComponent.geoVelocityOptions.DISABLED);
    this.emitRiskAssessmentChange();
  }

  private static buildAssessment(assessment, option) {
    if (option !== assessment) {
      const thresholds = {};
      thresholds[assessment.value] = assessment.threshold
      return {"enabled": true, "thresholds": thresholds};
    }
    return this.disabledAssessment();
  }

  private static disabledAssessment() {
    return {"enabled": false, "thresholds": {}};
  }

  private emitRiskAssessmentChange() {
    this.settingsChangeEmitter.emit({
      "deviceAssessment": this.currentDeviceAssessment,
      "ipReputationAssessment": this.currentIpReputationAssessment,
      "geoVelocityAssessment": this.currentGeoVelocityAssessment
    });
  }
}

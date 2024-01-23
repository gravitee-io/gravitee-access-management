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
import { Observable } from 'rxjs';
import { GioLicenseService, LicenseOptions } from '@gravitee/ui-particles-angular';

interface ModeOption {
  label: string;
  message: string;
  licenseOptions?: LicenseOptions;
  isMissingFeature$?: Observable<boolean>;
  warning?: string;
  warningLink?: string;
}

@Component({
  selector: 'mfa-activate',
  templateUrl: './mfa-activate.component.html',
  styleUrls: ['./mfa-activate.component.scss'],
})
export class MfaActivateComponent implements OnInit {
  private static modeOptions: Record<string, ModeOption> = {
    OPTIONAL: {
      label: 'Optional',
      message:
        'Users will be given the option to use MFA when signing in. You can specify the period of time during which enrollment can be skipped.',
    },
    REQUIRED: {
      label: 'Required',
      message: 'All users will be required to enrol with MFA during sign-in.',
    },
    CONDITIONAL: {
      label: 'Conditional',
      message:
        'Conditional allows you to configure the rules on how users will be enrolled to use MFA. This is done using Expression Language.',
      warning: 'To use the GeoIP based variables, ensure to install GeoIP service plugins on your application.',
      warningLink: 'https://docs.gravitee.io/am/current/am_userguide_mfa_amfa.html',
    },
  };

  @Input() enrollment: any;
  @Input() adaptiveMfaRule: string;
  @Input() skipAdaptiveMfaRule: string;
  @Output() settingsChange: EventEmitter<any> = new EventEmitter<any>();

  currentMode: any;
  modes: any[];
  enable = false;
  constructor(private licenseService: GioLicenseService) {}

  ngOnInit(): void {
    this.initModes();
    this.currentMode = MfaActivateComponent.modeOptions.OPTIONAL;
  }
  switchEnable(): void {
    this.enable = !this.enable;
    this.applyModeChange(this.currentMode);
  }

  get modeOptions() {
    return MfaActivateComponent.modeOptions;
  }

  applyModeChange(option: ModeOption): void {
    console.log('apply ', option);
    this.currentMode = option;
    switch (this.currentMode) {
      case MfaActivateComponent.modeOptions.OPTIONAL:
        this.updateOptional({ forceEnrollment: false, skipTimeSeconds: this.enrollment.skipTimeSeconds });
        break;
      case MfaActivateComponent.modeOptions.REQUIRED:
        this.updateRequired(this.enrollment);
        break;
      case MfaActivateComponent.modeOptions.CONDITIONAL:
        this.updateConditional(this.adaptiveMfaRule);
        break;
    }
  }
  isChecked(option: ModeOption): boolean {
    return this.currentMode === option;
  }

  updateOptional(timePicker: any): void {
    console.log('update optional', timePicker);
    this.settingsChange.emit({
      enrollment: { active: this.enable, forceEnrollment: false, skipTimeSeconds: timePicker?.skipTimeSeconds },
      adaptiveMfaRule: '',
      selectedMFAOption: MfaActivateComponent.modeOptions.OPTIONAL.label,
    });
  }

  updateRequired(enrollment: any): void {
    this.settingsChange.emit({
      enrollment: { active: this.enable, forceEnrollment: true, skipTimeSeconds: enrollment.skipTimeSeconds },
      adaptiveMfaRule: '',
      selectedMFAOption: MfaActivateComponent.modeOptions.REQUIRED.label,
    });
  }

  updateConditional(adaptiveMfaRule: any): void {
    console.log('mfa adaptive', adaptiveMfaRule);
    if (adaptiveMfaRule) {
      this.settingsChange.emit({
        enrollment: {active: this.enable, forceEnrollment: true, skipTimeSeconds: this.enrollment.skipTimeSeconds},
        adaptiveMfaRule: adaptiveMfaRule,
        selectedMFAOption: MfaActivateComponent.modeOptions.CONDITIONAL.label,
      });
    }
  }

  private initModes() {
    this.modes = Object.keys(MfaActivateComponent.modeOptions).map((key) => {
      const option = MfaActivateComponent.modeOptions[key];
      option.isMissingFeature$ = this.licenseService.isMissingFeature$(option.licenseOptions);
      return option;
    });
  }
}

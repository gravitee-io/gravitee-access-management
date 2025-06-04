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
import { Component, EventEmitter, Input, OnInit, Output, ChangeDetectorRef, AfterViewInit } from '@angular/core';
import { GioLicenseService } from '@gravitee/ui-particles-angular';

import { Enroll, ModeOption } from '../model';

@Component({
  selector: 'mfa-activate',
  templateUrl: './mfa-activate.component.html',
  styleUrls: ['./mfa-activate.component.scss'],
  standalone: false,
})
export class MfaActivateComponent implements OnInit, AfterViewInit {
  private static modeOptions: Record<string, ModeOption> = {
    OPTIONAL: {
      label: 'Optional',
      value: 'OPTIONAL',
      message:
        'Users will be given the option to use MFA when signing in. You can specify the period of time during which enrollment can be skipped.',
    },
    REQUIRED: {
      label: 'Required',
      value: 'REQUIRED',
      message: 'All users will be required to enrol with MFA during sign-in.',
    },
    CONDITIONAL: {
      label: 'Conditional',
      value: 'CONDITIONAL',
      message:
        'Conditional allows you to configure the rules on how users will be enrolled to use MFA. This is done using Expression Language.',
      warning: 'To use the GeoIP based variables, ensure to install GeoIP service plugins on your application.',
      warningLink: 'https://documentation.gravitee.io/am/guides/multi-factor-authentication',
    },
  };

  @Input() enrollment: Enroll;
  @Output() settingsChange = new EventEmitter<Enroll>();

  currentMode: any;
  modes: any[];
  constructor(
    private licenseService: GioLicenseService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.initModes();
    this.currentMode = this.enrollment.type
      ? MfaActivateComponent.modeOptions[this.enrollment.type.toUpperCase()]
      : MfaActivateComponent.modeOptions.OPTIONAL;
  }
  switchEnable(): void {
    this.enrollment.active = !this.enrollment.active;
    this.onOptionChange(this.currentMode);
  }
  get modeOptions() {
    return MfaActivateComponent.modeOptions;
  }
  onOptionChange(option: ModeOption): void {
    this.currentMode = option;
    this.update();
  }

  onSkipChange(skipTime: number): void {
    this.enrollment.skipTimeSeconds = skipTime;
    this.update();
  }
  onConditionalChange(enrollment: Enroll): void {
    this.enrollment.enrollmentSkipActive = enrollment.enrollmentSkipActive;
    this.enrollment.enrollmentRule = enrollment.enrollmentRule;
    this.enrollment.skipTimeSeconds = enrollment.skipTimeSeconds;
    this.update();
  }

  isChecked(option: ModeOption): boolean {
    return this.currentMode === option;
  }

  private initModes(): void {
    this.modes = Object.keys(MfaActivateComponent.modeOptions).map((key) => {
      const option = MfaActivateComponent.modeOptions[key];
      option.isMissingFeature$ = this.licenseService.isMissingFeature$(option.licenseOptions?.feature);
      return option;
    });
  }
  ngAfterViewInit(): void {
    this.cdr.detectChanges();
  }

  private update(): void {
    this.settingsChange.emit({
      active: this.enrollment.active,
      forceEnrollment: this.currentMode !== MfaActivateComponent.modeOptions.OPTIONAL,
      skipTimeSeconds: this.enrollment.skipTimeSeconds,
      enrollmentRule: this.currentMode === MfaActivateComponent.modeOptions.CONDITIONAL ? this.enrollment.enrollmentRule : '',
      enrollmentSkipActive:
        this.currentMode === MfaActivateComponent.modeOptions.CONDITIONAL ? this.enrollment.enrollmentSkipActive : false,
      enrollmentSkipRule: this.currentMode === MfaActivateComponent.modeOptions.CONDITIONAL ? this.enrollment.enrollmentSkipRule : '',
      type: this.currentMode.value,
    });
  }
}

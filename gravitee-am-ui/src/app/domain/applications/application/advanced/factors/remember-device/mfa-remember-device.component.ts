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
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { duration } from 'moment';

import { TimeConverterService } from '../../../../../../services/time-converter.service';

@Component({
  selector: 'mfa-remember-device',
  templateUrl: './mfa-remember-device.component.html',
  styleUrls: ['./mfa-remember-device.component.scss'],
})
export class MfaRememberDeviceComponent implements OnInit, OnChanges {
  @Input() rememberDevice: any;
  @Input() deviceIdentifiers: any[] = [];
  @Input() selectedMFAOption: any;
  @Input() enrollment: any;
  @Input() adaptiveMfaRule: string;
  @Input() riskAssessment: any;

  // eslint-disable-next-line @angular-eslint/no-output-rename
  @Output('settings-change') settingsChangeEmitter: EventEmitter<any> = new EventEmitter<any>();

  private humanTime: { expirationTime: any; expirationUnit: any };
  active: boolean;
  skipRememberDevice: boolean;
  selectedDeviceIdentifier: any;
  isConditional: any;

  constructor(private timeConverterService: TimeConverterService) {}

  ngOnInit(): void {
    this.active = this.rememberDevice ? this.rememberDevice.active : false;
    this.skipRememberDevice = this.rememberDevice ? this.rememberDevice.skipRememberDevice : false;
    this.isConditional = this.isConditionalConfig();
    const time = this.rememberDevice ? this.rememberDevice.expirationTimeSeconds || 36000 : 36000; // Default 10h
    if (this.hasDeviceIdentifierPlugins()) {
      this.selectedDeviceIdentifier = this.rememberDevice ? this.rememberDevice.deviceIdentifierId : null;
    }
    this.humanTime = {
      expirationTime: this.timeConverterService.getTime(time, 'seconds'),
      expirationUnit: this.timeConverterService.getUnitTime(time, 'seconds'),
    };
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.selectedMFAOption) {
      this.isConditional = changes.selectedMFAOption.currentValue === 'Conditional';
    }
  }

  displayExpiresIn() {
    return this.humanTime.expirationTime;
  }

  displayUnitTime() {
    return this.humanTime.expirationUnit;
  }

  hasDeviceIdentifierPlugins() {
    return this.deviceIdentifiers && this.deviceIdentifiers.length > 0;
  }

  updateDeviceIdentifierId($event) {
    this.selectedDeviceIdentifier = $event.value;
    this.updateRememberDeviceSettings({
      active: this.active,
      skipRememberDevice: this.skipRememberDevice,
      deviceIdentifierId: this.selectedDeviceIdentifier,
      expirationTimeSeconds: this.humanTimeToSeconds(),
    });
  }

  onToggle($event) {
    this.active = $event.checked;
    (this.skipRememberDevice = !this.active ? false : this.skipRememberDevice),
      this.updateRememberDeviceSettings({
        active: this.active,
        skipRememberDevice: this.skipRememberDevice,
        deviceIdentifierId: this.selectedDeviceIdentifier,
        expirationTimeSeconds: this.humanTimeToSeconds(),
      });
  }

  onSkip($event) {
    this.skipRememberDevice = $event.checked;
    this.updateRememberDeviceSettings({
      active: this.active,
      skipRememberDevice: this.skipRememberDevice,
      deviceIdentifierId: this.selectedDeviceIdentifier,
      expirationTimeSeconds: this.humanTimeToSeconds(),
    });
  }

  onTimeChange($event) {
    this.humanTime.expirationTime = Math.abs($event.target.value);
    this.updateRememberDeviceSettings({
      active: this.active,
      skipRememberDevice: this.skipRememberDevice,
      deviceIdentifierId: this.selectedDeviceIdentifier,
      expirationTimeSeconds: this.humanTimeToSeconds(),
    });
  }

  onUnitChange($event) {
    this.humanTime.expirationUnit = $event.value;
    this.updateRememberDeviceSettings({
      active: this.active,
      skipRememberDevice: this.skipRememberDevice,
      deviceIdentifierId: this.selectedDeviceIdentifier,
      expirationTimeSeconds: this.humanTimeToSeconds(),
    });
  }

  private humanTimeToSeconds() {
    return duration(this.humanTime.expirationTime, this.humanTime.expirationUnit).asSeconds();
  }

  private updateRememberDeviceSettings(rememberDevice) {
    if (this.hasDeviceIdentifierPlugins()) {
      this.settingsChangeEmitter.emit(rememberDevice);
    }
  }

  isActive() {
    return this.active;
  }

  isConditionalConfig() {
    if (this.riskAssessment && this.riskAssessment.enabled) {
      return false;
    } else {
      return this.adaptiveMfaRule && this.adaptiveMfaRule !== '';
    }
  }
}

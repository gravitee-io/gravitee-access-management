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
import { ActivatedRoute } from '@angular/router';

import { TimeConverterService } from '../../../../../../services/time-converter.service';
import { Enroll, RememberDevice } from '../model';

@Component({
  selector: 'mfa-remember-device',
  templateUrl: './mfa-remember-device.component.html',
  styleUrls: ['./mfa-remember-device.component.scss'],
})
export class MfaRememberDeviceComponent implements OnInit, OnChanges {
  @Input() rememberDevice: any;
  @Input() deviceIdentifiers: any[] = [];
  @Input() selectedMFAOption: any;
  @Input() enrollment: Enroll;
  @Input() adaptiveMfaRule: string;
  @Input() riskAssessment: any;

  @Output() settingsChange = new EventEmitter<RememberDevice>();

  private humanTime: { expirationTime: any; expirationUnit: any };
  active: boolean;
  skipRememberDevice: boolean;
  selectedDeviceIdentifier: any;

  domainId: string;
  environment: string;

  constructor(
    private timeConverterService: TimeConverterService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    this.active = this.rememberDevice ? this.rememberDevice.active : false;
    this.skipRememberDevice = this.rememberDevice ? this.rememberDevice.skipRememberDevice : false;
    const time = this.rememberDevice ? this.rememberDevice.expirationTimeSeconds || 36000 : 36000; // Default 10h
    if (this.hasDeviceIdentifierPlugins()) {
      this.selectedDeviceIdentifier = this.rememberDevice ? this.rememberDevice.deviceIdentifierId : null;
    }
    this.humanTime = {
      expirationTime: this.timeConverterService.getTime(time, 'seconds'),
      expirationUnit: this.timeConverterService.getUnitTime(time, 'seconds'),
    };
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.environment = this.route.snapshot.data['domain']?.referenceId;
  }

  getDeviceIdentifierLink(): string {
    return `/environments/${this.environment}/domains/${this.domainId}/settings/device-identifier`;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.selectedMFAOption) {
      if (!this.isConditional()) {
        this.skipRememberDevice = false;
        this.update();
      }
    }
  }

  displayExpiresIn(): any {
    return this.humanTime.expirationTime;
  }

  displayUnitTime(): any {
    return this.humanTime.expirationUnit;
  }

  hasDeviceIdentifierPlugins(): boolean {
    return this.deviceIdentifiers && this.deviceIdentifiers.length > 0;
  }

  updateDeviceIdentifierId($event: any): void {
    this.selectedDeviceIdentifier = $event.value;
    this.update();
  }

  onToggle($event: any): void {
    this.active = $event.checked;
    this.skipRememberDevice = this.active && this.skipRememberDevice;
    this.update();
  }

  onSkip($event: any): void {
    this.skipRememberDevice = $event.checked;
    this.update();
  }

  onTimeChange($event: any): void {
    this.humanTime.expirationTime = Math.abs($event.target.value);
    this.update();
  }

  onUnitChange($event: any): void {
    this.humanTime.expirationUnit = $event.value;
    this.update();
  }
  isConditional(): boolean {
    return this.selectedMFAOption?.toUpperCase() === 'CONDITIONAL';
  }

  private humanTimeToSeconds(): number {
    return duration(this.humanTime.expirationTime, this.humanTime.expirationUnit).asSeconds();
  }

  private update(): void {
    if (this.hasDeviceIdentifierPlugins() && this.humanTime?.expirationTime && this.humanTime?.expirationUnit) {
      this.settingsChange.emit({
        active: this.active,
        skipRememberDevice: this.skipRememberDevice,
        deviceIdentifierId: this.selectedDeviceIdentifier,
        expirationTimeSeconds: this.humanTimeToSeconds(),
      });
    }
  }
}

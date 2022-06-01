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
import {TimeConverterService} from "../../../../../../services/time-converter.service";
import moment from "moment";

@Component({
  selector: 'mfa-remember-device',
  templateUrl: './mfa-remember-device.component.html',
  styleUrls: ['./mfa-remember-device.component.scss']
})
export class MfaRememberDeviceComponent implements OnInit {

  @Input() rememberDevice: any;
  @Input() deviceIdentifiers: any[] = [];

  @Output("settings-change") settingsChangeEmitter: EventEmitter<any> = new EventEmitter<any>();

  private humanTime: { expirationTime: any; expirationUnit: any };
  active: boolean;
  selectedDeviceIdentifier: any;

  constructor(private timeConverterService: TimeConverterService) {
  }

  ngOnInit(): void {
    this.active = this.rememberDevice ? this.rememberDevice.active : false;
    const time = this.rememberDevice ? this.rememberDevice.expirationTimeSeconds || 36000 : 36000; // Default 10h
    if (this.hasDeviceIdentifierPlugins()) {
      this.selectedDeviceIdentifier = this.rememberDevice ? this.rememberDevice.deviceIdentifierId : null;
    }
    this.humanTime = {
      'expirationTime': this.timeConverterService.getTime(time, 'seconds'),
      'expirationUnit': this.timeConverterService.getUnitTime(time, 'seconds')
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
      "active": this.active,
      "deviceIdentifierId": this.selectedDeviceIdentifier,
      "expirationTimeSeconds": this.humanTimeToSeconds()
    });
  }

  onToggle($event) {
    this.active = $event.checked;
    this.updateRememberDeviceSettings({
      "active": this.active,
      "deviceIdentifierId": this.selectedDeviceIdentifier,
      "expirationTimeSeconds": this.humanTimeToSeconds()
    });
  }

  onTimeChange($event) {
    this.humanTime.expirationTime = Math.abs($event.target.value);
    this.updateRememberDeviceSettings({
      "active": this.active,
      "deviceIdentifierId": this.selectedDeviceIdentifier,
      "expirationTimeSeconds": this.humanTimeToSeconds()
    });
  }

  onUnitChange($event) {
    this.humanTime.expirationUnit = $event.value;
    this.updateRememberDeviceSettings({
      "active": this.active,
      "deviceIdentifierId": this.selectedDeviceIdentifier,
      "expirationTimeSeconds": this.humanTimeToSeconds()
    });
  }

  private humanTimeToSeconds() {
    return moment.duration(this.humanTime.expirationTime, this.humanTime.expirationUnit).asSeconds();
  }

  private updateRememberDeviceSettings(rememberDevice) {
    if (this.hasDeviceIdentifierPlugins()) {
      this.settingsChangeEmitter.emit(rememberDevice);
    }
  }
}

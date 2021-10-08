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
  selector: 'device-identifier-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss']
})
export class DeviceIdentifierCreationStep2Component implements OnInit {
  @Input('deviceIdentifier') deviceIdentifier: any;
  @Input('configurationIsValid') configurationIsValid: boolean;
  @Output('configurationIsValidChange') configurationIsValidChange: EventEmitter<boolean> = new EventEmitter<boolean>();
  formChanged = false;
  configuration: any;
  deviceIdentifierSchema: any = {};


  private deviceIdentifierTypes: any = {
    'fingerprintjs-v3-community-device-identifier': 'FingerprintJS v3 community',
    'fingerprintjs-v3-pro-device-identifier': 'FingerprintJS v3 Pro'
  };


  constructor(
    private organizationService: OrganizationService) { }

  ngOnInit() {
    this.organizationService.deviceIdentifiersSchema(this.deviceIdentifier.type).subscribe(data => {
      this.deviceIdentifierSchema = data;
    });
  }

  enableCreation(configurationWrapper) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.configurationIsValidChange.emit(this.configurationIsValid);
    this.deviceIdentifier.configuration = configurationWrapper.configuration;
  }

  displayType(type) {
    if (this.deviceIdentifierTypes[type]) {
      return this.deviceIdentifierTypes[type];
    }
    return type;
  }
}

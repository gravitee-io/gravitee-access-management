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
import { Component, OnInit, Input } from '@angular/core';
import { OrganizationService } from "../../../../../../services/organization.service";

@Component({
  selector: 'device-identifier-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss']
})
export class DeviceIdentifierCreationStep1Component implements OnInit {
  private deviceIdentifierTypes: any = {
    'fingerprintjs-v3-community-device-identifier' : 'FingerprintJS v3 community',
    'fingerprintjs-v3-pro-device-identifier' : 'FingerprintJS v3 Pro'
  };
  @Input() deviceIdentifier: any;
  deviceIdentifiers: any[];
  selectedDeviceIdentifierTypeId: string;

  constructor(private organizationService: OrganizationService) {
  }

  ngOnInit() {
    this.organizationService.deviceIdentifiers().subscribe(data => this.deviceIdentifiers = data);
  }

  selectType() {
    this.deviceIdentifier.type = this.selectedDeviceIdentifierTypeId;
  }

  displayName(detection) {
    if(this.deviceIdentifierTypes[detection.id]){
      return this.deviceIdentifierTypes[detection.id];
    }
    return detection.name;
  }
}

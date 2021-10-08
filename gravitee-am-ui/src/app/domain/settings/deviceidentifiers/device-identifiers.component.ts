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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';

@Component({
  selector: 'app-domain-device-identifiers',
  templateUrl: './device-identifiers.component.html',
  styleUrls: ['./device-identifiers.component.scss']
})
export class DomainSettingsDeviceIdentifiersComponent implements OnInit {
  private deviceIdentifierTypes: any = {
    'fingerprintjs-v3-community-device-identifier': 'FingerprintJS v3 community',
    'fingerprintjs-v3-pro-device-identifier': 'FingerprintJS v3 Pro'
  };
  deviceIdentifiers: any[];
  domainId: any;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    console.log(this.route.snapshot.data);
    this.deviceIdentifiers = this.route.snapshot.data['deviceIdentifiers'];
  }

  isEmpty() {
    return !this.deviceIdentifiers || this.deviceIdentifiers.length === 0;
  }

  displayType(type) {
    if (this.deviceIdentifierTypes[type]) {
      return this.deviceIdentifierTypes[type];
    }
    return type;
  }
}

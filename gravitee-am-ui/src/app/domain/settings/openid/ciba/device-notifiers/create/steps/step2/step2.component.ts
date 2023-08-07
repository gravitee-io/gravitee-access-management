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
import { ActivatedRoute } from '@angular/router';

import { OrganizationService } from '../../../../../../../../services/organization.service';

@Component({
  selector: 'device-notifier-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss'],
})
export class DeviceNotifierCreationStep2Component implements OnInit {
  @Input('deviceNotifier') deviceNotifier: any;
  @Input('configurationIsValid') configurationIsValid: boolean;
  @Output('configurationIsValidChange') configurationIsValidChange: EventEmitter<boolean> = new EventEmitter<boolean>();
  formChanged = false;
  configuration: any;
  deviceNotifierSchema: any = {};

  constructor(private organizationService: OrganizationService, private route: ActivatedRoute) {}

  ngOnInit() {
    this.organizationService.deviceNotifierSchema(this.deviceNotifier.type).subscribe((data) => {
      this.deviceNotifierSchema = data;
    });
  }

  enableDeviceNotifierCreation(configurationWrapper) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.configurationIsValidChange.emit(this.configurationIsValid);
    this.deviceNotifier.configuration = configurationWrapper.configuration;
  }
}

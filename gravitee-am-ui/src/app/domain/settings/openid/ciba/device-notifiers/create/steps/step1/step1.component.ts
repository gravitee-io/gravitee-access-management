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
import { OrganizationService } from "../../../../../../../../services/organization.service";

@Component({
  selector: 'device-notifier-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss']
})
export class DeviceNotifierCreationStep1Component implements OnInit {
  private deviceNotifierTypes: any = {
    'http-am-authdevice-notifier' : 'External HTTP Service'
  };
  @Input() deviceNotifier: any;
  deviceNotifiers: any[];
  selectedNotifierTypeId: string;

  constructor(private organizationService: OrganizationService) {
  }

  ngOnInit() {
    this.organizationService.deviceNotifiers(true).subscribe(data => {
      this.deviceNotifiers = data
    });
  }

  selectNotifierType() {
    this.deviceNotifier.type = this.selectedNotifierTypeId;
  }

  displayName(deviceNotifier) {
    if (this.deviceNotifierTypes[deviceNotifier.id]) {
      return this.deviceNotifierTypes[deviceNotifier.id];
    }
    return deviceNotifier.name;
  }

  getIcon(deviceNotifier) {
    if (deviceNotifier && deviceNotifier.icon) {
      const title = this.displayName(deviceNotifier);
      return `<img mat-card-avatar src="${deviceNotifier.icon}" alt="${title} image" title="${title}"/>`;
    }
    return `<i class="material-icons">system_update</i>`;
  }
}

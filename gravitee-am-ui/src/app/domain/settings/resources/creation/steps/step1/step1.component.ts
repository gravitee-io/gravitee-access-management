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
  selector: 'resource-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss']
})
export class ResourceCreationStep1Component implements OnInit {
  private resourceTypes: any = {
    'twilio-verify-am-resource' : 'Twilio Verify',
    'smtp-am-resource' : 'SMTP',
    'infobip-am-resource' : 'Infobip 2FA',
    'http-am-resource' : 'HTTP'
  };
  @Input() resource: any;
  resources: any[];

  constructor(private organizationService: OrganizationService) {
  }

  ngOnInit() {
    this.organizationService.resources(true).subscribe(data => this.resources = data);
  }

  selectResourceType(selectedResourceTypeId) {
    this.resource.type = selectedResourceTypeId;
  }

  displayName(resource) {
    if (this.resourceTypes[resource.id]) {
      return this.resourceTypes[resource.id];
    }
    return resource.name;
  }

  getIcon(resource) {
    if (resource && resource.icon) {
      const title = this.displayName(resource);
      return `<img mat-card-avatar src="${resource.icon}" alt="${title} image" title="${title}"/>`;
    }
    return `<i class="material-icons">mail_outline</i>`;
  }
}

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
  selector: 'app-domain-resources',
  templateUrl: './resources.component.html',
  styleUrls: ['./resources.component.scss']
})
export class DomainSettingsResourcesComponent implements OnInit {
  private resourceTypes: any = {
    'twilio-verify-am-resource' : 'Twilio Verify',
    'smtp-am-resource' : 'SMTP',
    'infobip-am-resource' : 'Infobip 2FA',
    'http-factor-am-resource' : 'HTTP Factor'
  };
  resources: any[];
  resourcePlugins: any[];
  domainId: any;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.resources = this.route.snapshot.data['resources'];
    this.resourcePlugins = this.route.snapshot.data['resourcePlugins'];
  }

  isEmpty() {
    return !this.resources || this.resources.length === 0;
  }

  displayType(type) {
    if (this.resourceTypes[type]) {
      return this.resourceTypes[type];
    }
    return type;
  }

  getResourceTypeIcon(type) {
    const res = this.getResourcePlugin(type);
    if (res && res.icon) {
      const name = this.displayType(type);
      return `<img width="24" height="24" src="${res.icon}" alt="${name} image" title="${name}"/>`;
    }
    return `<span class="material-icons">mail_outline</span>`;
  }

  private getResourcePlugin(type) {
    if (this.resourcePlugins) {
      return this.resourcePlugins.find(r => r.id === type);
    }
    return null;
  }

}

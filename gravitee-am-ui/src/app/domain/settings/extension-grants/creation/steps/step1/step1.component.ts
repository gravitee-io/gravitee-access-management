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

import { OrganizationService } from '../../../../../../services/organization.service';

@Component({
  selector: 'extension-grant-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss'],
  standalone: false,
})
export class ExtensionGrantCreationStep1Component implements OnInit {
  private extensionGrantTypes: any = {
    'jwtbearer-am-extension-grant': 'Extension Grant JWT Bearer',
    'token-exchange-am-extension-grant': 'Extension Grant Token Exchange'
  };
  @Input() extensionGrant: any;
  extensionGrants: any[];
  selectedExtensionGrantTypeId: string;

  constructor(private organizationService: OrganizationService) {}

  ngOnInit() {
    this.organizationService.extensionGrants().subscribe((data) => (this.extensionGrants = data));
  }

  selectExtensionGrantType() {
    this.extensionGrant.type = this.selectedExtensionGrantTypeId;
  }

  displayName(extensionGrant) {
    if (this.extensionGrantTypes[extensionGrant.id]) {
      return this.extensionGrantTypes[extensionGrant.id];
    }
    return extensionGrant.name;
  }
}

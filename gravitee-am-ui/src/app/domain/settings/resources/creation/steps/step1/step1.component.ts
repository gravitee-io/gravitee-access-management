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
import { Component, OnInit, Input, OnDestroy } from '@angular/core';
import { takeUntil, tap } from 'rxjs/operators';
import { Subject } from 'rxjs';

import { Plugin } from '../../../../../../entities/plugins/Plugin';
import { OrganizationService } from '../../../../../../services/organization.service';

@Component({
  selector: 'resource-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss'],
  standalone: false,
})
export class ResourceCreationStep1Component implements OnInit, OnDestroy {
  private resourceTypes: any = {
    'twilio-verify-am-resource': 'Twilio Verify',
    'smtp-am-resource': 'SMTP',
    'infobip-am-resource': 'Infobip 2FA',
    'http-factor-am-resource': 'HTTP Factor',
  };
  @Input() resource: any;
  resources: Plugin[];
  private unsubscribe$: Subject<boolean> = new Subject<boolean>();

  constructor(private organizationService: OrganizationService) {}

  ngOnInit() {
    this.organizationService
      .resources(true)
      .pipe(
        tap((resources) => {
          this.resources = resources;
        }),
        takeUntil(this.unsubscribe$),
      )
      .subscribe();
  }

  ngOnDestroy(): void {
    this.unsubscribe$.next(false);
    this.unsubscribe$.unsubscribe();
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
    if (resource?.icon) {
      const title = this.displayName(resource);
      return `<img mat-card-avatar src="${resource.icon}" alt="${title} image" title="${title}"/>`;
    }
    return `<i class="material-icons">mail_outline</i>`;
  }
}

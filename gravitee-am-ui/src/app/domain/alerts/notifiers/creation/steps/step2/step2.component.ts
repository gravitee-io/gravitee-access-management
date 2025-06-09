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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';

import { OrganizationService } from '../../../../../../services/organization.service';

@Component({
  selector: 'alert-notifier-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss'],
  standalone: false,
})
export class DomainAlertNotifierCreationStep2Component implements OnChanges {
  @Input() alertNotifier: any;
  @Input() configurationIsValid: boolean;
  @Output() configurationIsValidChange: EventEmitter<boolean> = new EventEmitter<boolean>();
  configuration: any;
  notifierSchema: any = {};

  constructor(private organizationService: OrganizationService) {}

  ngOnChanges(changes: SimpleChanges) {
    if (changes.alertNotifier) {
      this.organizationService.notifierSchema(changes.alertNotifier.currentValue.type).subscribe((data) => {
        this.notifierSchema = data;
      });
    }
  }

  enableNotifierCreation(configurationWrapper) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.configurationIsValidChange.emit(this.configurationIsValid);
    this.alertNotifier.configuration = configurationWrapper.configuration;
  }
}

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
  selector: 'resource-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss']
})
export class ResourceCreationStep2Component implements OnInit {
  @Input('resource') resource: any;
  @Input('configurationIsValid') configurationIsValid: boolean;
  @Output('configurationIsValidChange') configurationIsValidChange: EventEmitter<boolean> = new EventEmitter<boolean>();
  formChanged = false;
  configuration: any;
  resourceSchema: any = {};

  constructor(private organizationService: OrganizationService) { }

  ngOnInit() {
    this.organizationService.resourceSchema(this.resource.type).subscribe(data => {
      this.resourceSchema = data;
    });
  }

  enableResourceCreation(configurationWrapper) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.configurationIsValidChange.emit(this.configurationIsValid);
    this.resource.configuration = configurationWrapper.configuration;
  }
}

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

import { OrganizationService } from '../../../../../../services/organization.service';

@Component({
  selector: 'bot-detection-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss'],
  standalone: false,
})
export class BotDetectionCreationStep2Component implements OnInit {
  @Input() botDetection: any;
  @Input() configurationIsValid: boolean;
  @Output() configurationIsValidChange: EventEmitter<boolean> = new EventEmitter<boolean>();
  formChanged = false;
  configuration: any;
  botDetectionSchema: any = {};

  constructor(private organizationService: OrganizationService) {}

  ngOnInit() {
    this.organizationService.botDetectionsSchema(this.botDetection.type).subscribe((data) => {
      this.botDetectionSchema = data;

      if (this.botDetectionSchema.properties.detectionType) {
        this.botDetection.detectionType = this.botDetectionSchema.properties.detectionType.default;
      }
    });
  }

  enableBotDetectionCreation(configurationWrapper) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.configurationIsValidChange.emit(this.configurationIsValid);
    this.botDetection.configuration = configurationWrapper.configuration;
  }
}

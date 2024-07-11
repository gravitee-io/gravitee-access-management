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
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';

import { OrganizationService } from '../../../../../../services/organization.service';

@Component({
  selector: 'certificate-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss'],
})
export class CertificateCreationStep2Component implements OnInit, OnChanges {
  @Input() certificate: any;
  @Input() configurationIsValid: boolean;
  @Output() configurationIsValidChange = new EventEmitter<boolean>();
  configuration: any;
  description: string;
  certificateSchema: any = {};
  constructor(private organizationService: OrganizationService) {}

  ngOnInit(): void {
    this.organizationService.certificateSchema(this.certificate.type).subscribe((data) => {
      this.certificateSchema = data;
      this.description = data.description || 'Configure your certificate.';
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.certificate?.currentValue?.configuration === null) {
      this.clearFormWithErrors();
    }
  }

  enableCertificateCreation(configurationWrapper: any): void {
    this.configurationIsValid = configurationWrapper.isValid;
    this.configurationIsValidChange.emit(this.configurationIsValid);
    this.certificate.configuration = configurationWrapper.configuration;
  }
  private clearFormWithErrors(): void {
    this.organizationService.certificateSchema(this.certificate.type).subscribe((data) => {
      this.certificateSchema = data;
      this.configuration = {
        storepass: ' ',
        keypass: ' ',
        alias: ' ',
      };
      setTimeout(() => {
        this.configuration = {
          storepass: '',
          keypass: '',
          alias: '',
        };
      }, 0);
    });
  }
}

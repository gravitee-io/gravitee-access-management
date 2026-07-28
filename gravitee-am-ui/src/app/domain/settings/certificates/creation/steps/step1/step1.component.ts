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
import { switchMap } from 'rxjs/operators';

import { OrganizationService } from '../../../../../../services/organization.service';
import { LicensedPlugin, PluginFeatureService } from '../../../../../../services/plugin-feature.service';

@Component({
  selector: 'certificate-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss'],
  standalone: false,
})
export class CertificateCreationStep1Component implements OnInit {
  private certificateTypes: any = {
    'javakeystore-am-certificate': 'Java Keystore (.jks)',
    'pkcs12-am-certificate': 'PKCS#12 (.p12)',
    'aws-am-certificate': 'AWS Secret Manager',
  };
  @Input() certificate: any;
  certificates: (any & LicensedPlugin)[];
  selectedCertificateTypeId: string;

  constructor(
    private organizationService: OrganizationService,
    private pluginFeatureService: PluginFeatureService,
  ) {}

  ngOnInit(): void {
    this.organizationService
      .certificates()
      .pipe(switchMap((data) => this.pluginFeatureService.decorateCatalog$(data)))
      .subscribe((certificates) => (this.certificates = certificates));
  }

  selectCertificateType(): void {
    this.certificate.type = this.selectedCertificateTypeId;
  }

  displayName(certificate: any): string {
    if (this.certificateTypes[certificate.id]) {
      return this.certificateTypes[certificate.id];
    }
    return certificate.name;
  }
}

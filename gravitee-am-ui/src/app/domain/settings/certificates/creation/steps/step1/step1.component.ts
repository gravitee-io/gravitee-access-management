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
import { Component, OnInit, Output, EventEmitter, Input, OnChanges, SimpleChanges } from '@angular/core';
import { PlatformService } from "../../../../../../services/platform.service";

@Component({
  selector: 'certificate-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss']
})
export class CertificateCreationStep1Component implements OnInit, OnChanges {
  @Input() certificate: any = {};
  @Output() certificateTypeSelected = new EventEmitter<string>();
  @Output() nextStepTriggered = new EventEmitter<boolean>();
  certificates: any[];
  selectedCertificateTypeId : string;

  constructor(private platformService: PlatformService) {
  }

  ngOnInit() {
    this.platformService.certificates().map(res => res.json()).subscribe(data => this.certificates = data);
  }

  ngOnChanges(changes: SimpleChanges) {
    let _certificate = changes.certificate.currentValue;
    if (_certificate.type) {
      this.selectedCertificateTypeId = _certificate.type;
    }
  }

  selectCertificateType() {
    this.certificateTypeSelected.emit(this.selectedCertificateTypeId);
  }

  nextStep() {
    this.nextStepTriggered.emit(true);
  }
}

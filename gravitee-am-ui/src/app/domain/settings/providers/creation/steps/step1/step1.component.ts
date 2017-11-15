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
import { PlatformService } from "../../../../../shared/services/platform.service";

@Component({
  selector: 'provider-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss']
})
export class ProviderCreationStep1Component implements OnInit, OnChanges {
  @Input() provider: any = {};
  @Output() providerTypeSelected = new EventEmitter<string>();
  @Output() nextStepTriggered = new EventEmitter<boolean>();
  providers: any[];
  selectedProviderTypeId : string;

  constructor(private platformService: PlatformService) {
  }

  ngOnInit() {
    this.platformService.identities().map(res => res.json()).subscribe(data => this.providers = data);
  }

  ngOnChanges(changes: SimpleChanges) {
    let _provider = changes.provider.currentValue;
    if (_provider.type) {
      this.selectedProviderTypeId = _provider.type;
    }
  }

  selectProviderType() {
    this.providerTypeSelected.emit(this.selectedProviderTypeId);
  }

  nextStep() {
    this.nextStepTriggered.emit(true);
  }
}

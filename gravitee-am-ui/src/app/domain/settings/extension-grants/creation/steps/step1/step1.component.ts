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
  selector: 'extension-grant-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss']
})
export class ExtensionGrantCreationStep1Component implements OnInit, OnChanges {
  @Input() extensionGrant: any = {};
  @Output() extensionGrantTypeSelected = new EventEmitter<string>();
  @Output() nextStepTriggered = new EventEmitter<boolean>();
  extensionGrants: any[];
  selectedExtensionGrantTypeId : string;

  constructor(private platformService: PlatformService) {
  }

  ngOnInit() {
    this.platformService.extensionGrants().map(res => res.json()).subscribe(data => this.extensionGrants = data);
  }

  ngOnChanges(changes: SimpleChanges) {
    let _extensionGrant = changes.extensionGrant.currentValue;
    if (_extensionGrant && _extensionGrant.type) {
      this.selectedExtensionGrantTypeId = _extensionGrant.type;
    }
  }

  selectExtensionGrantType() {
    this.extensionGrantTypeSelected.emit(this.selectedExtensionGrantTypeId);
  }

  nextStep() {
    this.nextStepTriggered.emit(true);
  }
}

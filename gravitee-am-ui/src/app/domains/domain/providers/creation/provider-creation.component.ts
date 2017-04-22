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
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-creation',
  templateUrl: 'provider-creation.component.html',
  styleUrls: ['provider-creation.component.scss']
})
export class ProviderCreationComponent implements OnInit {
  private provider: any = {};
  currentStep: number;

  constructor() { }

  ngOnInit() {
    this.currentStep = 1;
  }

  selectProviderType(event) {
    this.provider.type = event;
  }

  nextStep() {
    this.currentStep += 1;
  }

  previousStep() {
    this.currentStep -= 1;
  }
}

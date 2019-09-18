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

@Component({
  selector: 'application-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss']
})
export class ApplicationCreationStep1Component {
  @Input() application;
  applicationTypes: any[] = [
    {
      name: 'Web',
      icon: 'language',
      type: 'WEB',
      description: 'Traditional web apps',
      subDescription: 'e.g : .NET, Java'
    },
    {
      name: 'Single-Page App',
      icon: 'web',
      type: 'BROWSER',
      description: 'JavaScript front-end apps',
      subDescription: 'e.g : Angular, React, VueJS'
    },
    {
      name: 'Native',
      icon: 'devices_other',
      type: 'NATIVE',
      description: 'Mobile, Desktop apps',
      subDescription: 'e.g : iOS, Android'
    },
    {
      name: 'Backend to Backend',
      icon: 'storage',
      type: 'SERVICE',
      description: 'Machine-to-Machine apps',
      subDescription : 'e.g : Shell script, daemon, CLI'
    }];

  constructor() {}

  selectApplicationType(selectedApplicationType) {
    this.application.type = selectedApplicationType;
  }
}

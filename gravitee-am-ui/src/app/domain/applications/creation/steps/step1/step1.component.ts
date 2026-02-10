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
import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { remove } from 'lodash';

@Component({
  selector: 'application-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss'],
  standalone: false,
})
export class ApplicationCreationStep1Component implements OnInit {
  @Input() application;
  private domain: any;
  applicationTypes: any[] = [
    {
      name: 'Web',
      icon: 'language',
      type: 'WEB',
      description: 'Traditional web apps',
      subDescription: 'e.g : .NET, Java',
    },
    {
      name: 'Single-Page App',
      icon: 'web',
      type: 'BROWSER',
      description: 'JavaScript front-end apps',
      subDescription: 'e.g : Angular, React, VueJS',
    },
    {
      name: 'Native',
      icon: 'devices_other',
      type: 'NATIVE',
      description: 'Mobile, Desktop apps',
      subDescription: 'e.g : iOS, Android',
    },
    {
      name: 'Agentic Application',
      icon: 'device_hub',
      type: 'AGENT',
      description: 'Agentic apps',
      subDescription: 'e.g : AI assistants, autonomous agents',
    },
    {
      name: 'Backend to Backend',
      icon: 'storage',
      type: 'SERVICE',
      description: 'Machine-to-Machine apps',
      subDescription: 'e.g : Shell script, daemon, CLI',
    },
    {
      name: 'Resource Server',
      icon: 'folder_shared',
      type: 'RESOURCE_SERVER',
      description: 'Resource Server apps',
      subDescription: 'e.g : APIs',
    },
  ];

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.domain = this.route.snapshot.data['domain'];
    if (!this.domain.uma || !this.domain.uma.enabled) {
      remove(this.applicationTypes, { type: 'RESOURCE_SERVER' });
    }
  }

  selectApplicationType(selectedApplicationType: string): void {
    this.application.type = selectedApplicationType;
  }
}

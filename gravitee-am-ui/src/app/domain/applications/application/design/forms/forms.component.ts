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
import { ActivatedRoute } from '@angular/router';

import { FormTemplateFactoryService } from '../../../../../services/form.template.factory.service';

@Component({
  selector: 'app-application-forms',
  templateUrl: './forms.component.html',
  styleUrls: ['./forms.component.scss'],
})
export class ApplicationFormsComponent implements OnInit {
  domain: any;
  application: any;

  constructor(private route: ActivatedRoute, private formTemplateFactoryService: FormTemplateFactoryService) {}

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.application = this.route.snapshot.data['application'];
  }

  getForms() {
    return this.formTemplateFactoryService.findAll().map((form) => {
      form.enabled = form.template === 'ERROR' || this.applicationSettingsValid();
      return form;
    });
  }

  private applicationSettingsValid() {
    if (this.application.type) {
      return this.application.type !== 'service';
    }
    if (this.application.settings && this.application.settings.oauth && this.application.settings.oauth.grantTypes) {
      return (
        this.application.settings.oauth.grantTypes.includes('authorization_code') ||
        this.application.settings.oauth.grantTypes.includes('implicit')
      );
    }
    return false;
  }
}

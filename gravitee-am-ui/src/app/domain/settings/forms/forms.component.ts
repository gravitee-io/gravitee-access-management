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
import { ActivatedRoute, Router } from '@angular/router';

import { FormTemplateFactoryService } from '../../../services/form.template.factory.service';

@Component({
  selector: 'app-domain-forms',
  templateUrl: './forms.component.html',
  styleUrls: ['./forms.component.scss'],
  standalone: false,
})
export class DomainSettingsFormsComponent implements OnInit {
  forms: any[];
  domain: any;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private formTemplateFactoryService: FormTemplateFactoryService,
  ) {}

  ngOnInit() {
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.forms = this.getOrganizationForms();
    } else {
      this.domain = this.route.snapshot.data['domain'];
      this.forms = this.getForms();
    }
  }

  getForms() {
    return this.formTemplateFactoryService.findAll().map((form) => {
      if (form.template === 'MAGIC_LINK_LOGIN') {
        form.enabled = this.allowMagicLink();
        return form;
      }
      form.enabled = true;
      return form;
    });
  }

  getOrganizationForms() {
    return this.formTemplateFactoryService
      .findBy((form) => form.template === 'LOGIN')
      .map((form) => {
        form.enabled = true;
        return form;
      });
  }

  private allowMagicLink(): boolean {
    return this.domain.loginSettings?.magicLinkAuthEnabled;
  }
}

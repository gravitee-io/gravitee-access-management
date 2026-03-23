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
import { MatDialog } from '@angular/material/dialog';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { DomainStoreService } from '../../../../../stores/domain.store';
import { FormService } from '../../../../../services/form.service';
import { FormTemplateFactoryService } from '../../../../../services/form.template.factory.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { CreateCustomFormDialogComponent } from '../../../../components/forms/create-custom-form-dialog/create-custom-form-dialog.component';

@Component({
  selector: 'app-application-forms',
  templateUrl: './forms.component.html',
  styleUrls: ['./forms.component.scss'],
  standalone: false,
})
export class ApplicationFormsComponent implements OnInit {
  domain: any;
  application: any;
  customForms: any[] = [];
  templateForms: any[] = [];
  isLoading = false;

  constructor(
    private route: ActivatedRoute,
    private formTemplateFactoryService: FormTemplateFactoryService,
    private formService: FormService,
    private domainStore: DomainStoreService,
    private snackbarService: SnackbarService,
    private dialog: MatDialog,
  ) {}

  ngOnInit() {
    this.domain = deepClone(this.domainStore.current);
    this.application = this.route.snapshot.data['application'];
    this.loadForms();
  }

  loadForms() {
    this.isLoading = true;
    // Load template forms
    this.templateForms = this.formTemplateFactoryService.findAll().map((form) => {
      if (form.template === 'MAGIC_LINK_LOGIN') {
        form.enabled = this.allowMagicLink();
        return form;
      }
      form.enabled = form.template === 'ERROR' || this.applicationSettingsValid();
      return form;
    });

    // Load custom forms for the application
    this.formService.findCustoms(this.domain.id, this.application.id).subscribe({
      next: (response: any) => {
        if (response && Array.isArray(response)) {
          this.customForms = response.map((form) => ({
            ...form,
            enabled: true,
            isTemplate: false,
            icon: 'edit',
            name: form.template || form.id,
          }));
        }
        this.isLoading = false;
      },
      error: (error: unknown) => {
        const errorMessage = error instanceof Error ? error.message : String(error);
        this.snackbarService.open(`Error loading custom forms: ${errorMessage}`);
        this.isLoading = false;
      },
    });
  }

  openCreateFormDialog() {
    const dialogRef = this.dialog.open(CreateCustomFormDialogComponent, {
      width: '700px',
      data: {
        domainId: this.domain.id,
        applicationId: this.application.id,
      },
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.loadForms();
      }
    });
  }

  delete(formId: string) {
    this.formService.delete(this.domain.id, this.application.id, formId, false).subscribe(() => {
      this.snackbarService.open('Form deleted');
      this.loadForms();
    });
  }

  private applicationSettingsValid(): boolean {
    if (this.application.type) {
      return this.application.type !== 'service';
    }
    if (this.application.settings?.oauth?.grantTypes) {
      return (
        this.application.settings.oauth.grantTypes.includes('authorization_code') ||
        this.application.settings.oauth.grantTypes.includes('implicit')
      );
    }
    return false;
  }

  private allowMagicLink(): boolean {
    if (this.application.settings?.login && !this.application.settings.login.inherited) {
      return this.application.settings.login.magicLinkAuthEnabled;
    }
    return this.domain.loginSettings?.magicLinkAuthEnabled;
  }
}

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
import { AfterViewInit, Component, ElementRef, Inject, Input, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgForm } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { filter, switchMap, tap } from 'rxjs/operators';

import { SnackbarService } from '../../../../services/snackbar.service';
import { DialogService } from '../../../../services/dialog.service';
import { EmailService } from '../../../../services/email.service';
import { EmailTemplateFactoryService } from '../../../../services/email.template.factory.service';
import { TimeConverterService } from '../../../../services/time-converter.service';

export interface DialogData {
  rawTemplate: string;
  template: string;
}

@Component({
  selector: 'app-email',
  templateUrl: './email.component.html',
  styleUrls: ['./email.component.scss'],
  standalone: false,
})
export class EmailComponent implements OnInit, AfterViewInit {
  private domainId: string;
  private appId: string;
  private defaultEmailContent = `// Custom email...`;
  template: string;
  email: any;
  rawTemplate: string;
  emailName: string;
  emailContent: string = (' ' + this.defaultEmailContent).slice(1);
  originalEmailContent: string = (' ' + this.emailContent).slice(1);
  emailFound = false;
  formChanged = false;
  config: any = { lineNumbers: true, readOnly: true };
  defaultExpirationSeconds: number;
  @ViewChild('preview', { static: true }) preview: ElementRef;
  @ViewChild('emailForm', { static: true }) public emailForm: NgForm;
  @Input() createMode: boolean;
  @Input() editMode: boolean;
  @Input() deleteMode: boolean;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private emailService: EmailService,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private emailTemplateFactoryService: EmailTemplateFactoryService,
    private timeConverterService: TimeConverterService,
    public dialog: MatDialog,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.appId = this.route.snapshot.params['appId'];

    const rawTemplate = this.route.snapshot.queryParams['template'];
    const emailTemplate = this.emailTemplateFactoryService.findByName(rawTemplate);

    this.rawTemplate = emailTemplate.template;

    this.email = this.route.snapshot.data['email'];

    if (this.email?.content) {
      this.emailContent = this.email.content;
      this.originalEmailContent = (' ' + this.emailContent).slice(1);
      this.emailFound = true;
    } else {
      this.email = {};
      this.email.template = emailTemplate.template;
      this.email.expiresAfter = emailTemplate.defaultExpirationSeconds;
    }
    this.template = this.rawTemplate.toLowerCase().replace(/_/g, ' ');
    this.defaultExpirationSeconds = emailTemplate.defaultExpirationSeconds;
    this.emailName = emailTemplate.name;
  }

  ngAfterViewInit(): void {
    this.enableCodeMirror();
  }

  isEnabled(): boolean {
    return this.email?.enabled;
  }

  enableEmail(event) {
    this.email.enabled = event.checked;
    this.enableCodeMirror();
    this.formChanged = true;
  }

  onTabSelectedChanged(e) {
    if (e.index === 1) {
      this.refreshPreview();
    }
  }

  refreshPreview() {
    const iframe = this.preview.nativeElement;
    iframe.addEventListener('load', () => {
      const doc = iframe.contentDocument || iframe.contentWindow;
      doc.open();
      doc.write(this.emailContent);
      doc.close();
    });
  }

  onContentChanges(e) {
    if (e !== this.originalEmailContent) {
      this.formChanged = true;
    }
  }

  resizeIframe() {
    this.preview.nativeElement.style.height = this.preview.nativeElement.contentWindow.document.body.scrollHeight + 'px';
  }

  canEdit(): boolean {
    return this.emailFound ? this.editMode : this.createMode;
  }

  save() {
    if (!this.emailFound) {
      this.create();
    } else {
      this.update();
    }
  }

  create() {
    this.email['content'] = this.emailContent;
    this.emailService.create(this.domainId, this.appId, this.email).subscribe((data) => {
      this.snackbarService.open('Email created');
      this.emailFound = true;
      this.email = data;
      this.formChanged = false;
      this.emailForm.reset(this.email);
    });
  }

  update() {
    this.email['content'] = this.emailContent;
    this.emailService.update(this.domainId, this.appId, this.email.id, this.email).subscribe((data) => {
      this.snackbarService.open('Email updated');
      this.emailFound = true;
      this.email = data;
      this.formChanged = false;
      this.emailForm.reset(this.email);
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete email', 'Are you sure you want to delete this email ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.emailService.delete(this.domainId, this.appId, this.email.id)),
        tap(() => {
          this.snackbarService.open('Email deleted');
          this.email = {};
          this.email.template = this.route.snapshot.queryParams['template'];
          this.email.expiresAfter = 86400;
          this.emailContent = (' ' + this.defaultEmailContent).slice(1);
          this.originalEmailContent = (' ' + this.emailContent).slice(1);
          this.emailFound = false;
          this.formChanged = false;
          this.enableCodeMirror();
        }),
      )
      .subscribe();
  }

  openDialog() {
    this.dialog.open(EmailInfoDialogComponent, {
      data: { rawTemplate: this.rawTemplate, template: this.template },
    });
  }

  isFormInvalid() {
    return (this.emailForm.pristine || !this.emailForm.valid) && !this.formChanged;
  }

  private enableCodeMirror(): void {
    this.config.readOnly = !this.email.enabled;
  }

  getHumanTime(defaultExpirationSeconds: number) {
    return this.timeConverterService.getHumanTime(defaultExpirationSeconds);
  }
}

@Component({
  selector: 'email-info-dialog',
  templateUrl: './dialog/email-info.component.html',
  standalone: false,
})
export class EmailInfoDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<EmailInfoDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: DialogData,
  ) {}
}

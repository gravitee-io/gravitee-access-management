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
import {AfterViewInit, Component, ElementRef, Inject, Input, OnInit, ViewChild} from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import { SnackbarService } from "../../../../services/snackbar.service";
import { DialogService } from "../../../../services/dialog.service";
import { EmailService } from "../../../../services/email.service";
import { NgForm } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material";

export interface DialogData {
  rawTemplate: string;
  template: string;
}

@Component({
  selector: 'app-email',
  templateUrl: './email.component.html',
  styleUrls: ['./email.component.scss']
})
export class EmailComponent implements OnInit, AfterViewInit {
  private domainId: string;
  private appId: string;
  private defaultEmailContent = `// Custom email...`;
  template: string;
  rawTemplate: string;
  email: any;
  emailName: string;
  emailContent: string = (' ' + this.defaultEmailContent).slice(1);
  originalEmailContent: string = (' ' + this.emailContent).slice(1);
  emailFound = false;
  formChanged = false;
  config: any = { lineNumbers: true, readOnly: true};
  @ViewChild('editor', { static: true }) editor: any;
  @ViewChild('preview', { static: true }) preview: ElementRef;
  @ViewChild('emailForm', { static: true }) public emailForm: NgForm;
  @Input('createMode') createMode: boolean;
  @Input('editMode') editMode: boolean;
  @Input('deleteMode') deleteMode: boolean;

  constructor(private router: Router,
              private route: ActivatedRoute,
              private emailService: EmailService,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              public dialog: MatDialog) { }

  ngOnInit() {
    this.domainId = (this.route.snapshot.parent.parent.params['domainId']) ? this.route.snapshot.parent.parent.params['domainId'] : this.route.snapshot.parent.parent.parent.params['domainId'];
    this.appId = this.route.snapshot.parent.parent.params['appId'];
    this.rawTemplate = this.route.snapshot.queryParams['template'];
    this.email = this.route.snapshot.data['email']

    if (this.email && this.email.content) {
      this.emailContent = this.email.content;
      this.originalEmailContent = (' ' + this.emailContent).slice(1);
      this.emailFound = true;
    } else {
      this.email = {};
      this.email.template = this.rawTemplate
      this.email.expiresAfter = 86400;
    }
    this.template = this.rawTemplate.toLowerCase().replace(/_/g, ' ');
    this.emailName = this.template.charAt(0).toUpperCase() + this.template.slice(1);
  }

  ngAfterViewInit(): void {
    this.enableCodeMirror();
  }

  isEnabled() {
    return this.email && this.email.enabled;
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
    let doc =  this.preview.nativeElement.contentDocument || this.preview.nativeElement.contentWindow;
    doc.open();
    doc.write(this.emailContent);
    doc.close();
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
    this.emailService.create(this.domainId, this.appId, this.email).subscribe(data => {
      this.snackbarService.open('Email created');
      this.emailFound = true;
      this.email = data;
      this.formChanged = false;
      this.emailForm.reset(this.email);
    })
  }

  update() {
    this.email['content'] = this.emailContent;
    this.emailService.update(this.domainId, this.appId, this.email.id, this.email).subscribe(data => {
      this.snackbarService.open('Email updated');
      this.emailFound = true;
      this.email = data;
      this.formChanged = false;
      this.emailForm.reset(this.email);
    })
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete email', 'Are you sure you want to delete this email ?')
      .subscribe(res => {
        if (res) {
          this.emailService.delete(this.domainId, this.appId, this.email.id).subscribe(response => {
            this.snackbarService.open('Email deleted');
            this.email = {};
            this.email.template = this.route.snapshot.queryParams['template'];
            this.email.expiresAfter = 86400;
            this.emailContent =  (' ' + this.defaultEmailContent).slice(1);
            this.originalEmailContent = (' ' + this.emailContent).slice(1);
            this.emailFound = false;
            this.formChanged = false;
            this.enableCodeMirror();
          });
        }
      });
  }

  openDialog() {
    this.dialog.open(EmailInfoDialog, {
      data: {rawTemplate: this.rawTemplate, template: this.template}
    });
  }

  isFormInvalid() {
    return (this.emailForm.pristine || !this.emailForm.valid) && !this.formChanged;
  }

  private enableCodeMirror(): void {
    this.editor.instance.setOption('readOnly', !this.email.enabled);
  }
}

@Component({
  selector: 'email-info-dialog',
  templateUrl: './dialog/email-info.component.html',
})
export class EmailInfoDialog {
  constructor(public dialogRef: MatDialogRef<EmailInfoDialog>, @Inject(MAT_DIALOG_DATA) public data: DialogData) {}
}

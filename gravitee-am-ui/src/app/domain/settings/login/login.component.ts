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
import { AfterViewInit, Component, ElementRef, OnInit, ViewChild } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { DomainService } from "../../../services/domain.service";
import { SnackbarService } from "../../../services/snackbar.service";
import { DialogService } from "../../../services/dialog.service";
import { MatDialog, MatDialogRef } from "@angular/material/dialog";

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class DomainSettingsLoginComponent implements OnInit, AfterViewInit {
  domainId: string;
  domainLoginForm: any = {};
  loginFormContent:string = `// Custom login form...`;
  originalFormContent: string = (' ' + this.loginFormContent).slice(1);
  config:any = { lineNumbers: true, readOnly: true};
  loginFormFound: boolean = false;
  formChanged: boolean = false;
  @ViewChild('editor') editor: any;
  @ViewChild('preview') preview: ElementRef;

  constructor(private route: ActivatedRoute, private domainService: DomainService, private snackbarService: SnackbarService,
              private dialogService: DialogService, public dialog: MatDialog) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.domainLoginForm = this.route.snapshot.data['domainLoginForm'];
    if (this.domainLoginForm && this.domainLoginForm.content) {
      this.loginFormContent = this.domainLoginForm.content;
      this.originalFormContent = (' ' + this.loginFormContent).slice(1);
      this.loginFormFound = true;
    }
  }

  ngAfterViewInit(): void {
    this.enableCodeMirror();
  }

  create() {
    this.domainLoginForm['content'] = this.loginFormContent;
    this.domainService.createLoginForm(this.domainId, this.domainLoginForm).map(res => res.json()).subscribe(data => {
      this.snackbarService.open("Login form " + (this.loginFormFound ? 'updated' : 'created'));
      this.loginFormFound = true;
      this.domainLoginForm = data;
      this.formChanged = false;
    })
  }

  onContentChanges(e) {
    if (e !== this.originalFormContent) {
      this.formChanged = true;
    }
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Login form', 'Are you sure you want to delete this login form ?')
      .subscribe(res => {
        if (res) {
          this.domainService.deleteLoginForm(this.domainId).subscribe(response => {
            this.snackbarService.open("Login form deleted");
            this.domainLoginForm = {};
            this.loginFormContent = this.originalFormContent;
            this.loginFormFound = false;
          });
        }
      });
  }

  isEnabled() {
    return this.domainLoginForm && this.domainLoginForm.enabled;
  }

  enableLoginForm(event) {
    this.domainLoginForm.enabled = event.checked;
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
    doc.write(this.loginFormContent);
    doc.close();
  }

  resizeIframe() {
    this.preview.nativeElement.style.height = this.preview.nativeElement.contentWindow.document.body.scrollHeight + 'px';
  }

  openLoginInfo() {
    this.dialog.open(DomainSettingsLoginInfoDialog);
  }

  private enableCodeMirror(): void {
    this.editor.instance.setOption('readOnly', !this.domainLoginForm.enabled);
  }
}


@Component({
  selector: 'login-info-dialog',
  templateUrl: './dialog/login-info.component.html',
})
export class DomainSettingsLoginInfoDialog {
  constructor(public dialogRef: MatDialogRef<DomainSettingsLoginInfoDialog>) {}
}

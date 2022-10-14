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
import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {SnackbarService} from '../../../services/snackbar.service';
import {AuthService} from '../../../services/auth.service';
import {ThemeService} from '../../../services/theme.service';
import {DialogService} from '../../../services/dialog.service';
import {FormService} from '../../../services/form.service';
import * as _ from 'lodash';
import {forkJoin, of} from 'rxjs';

@Component({
  selector: 'app-theme',
  templateUrl: './theme.component.html',
  styleUrls: ['./theme.component.scss']
})
export class DomainSettingsThemeComponent implements OnInit {
  private envId: string;
  domain: any = {};
  readonly = false;
  themes: any[];
  theme: any;
  colorPalettes: any[] = [
    { 'name': 'light-blue', 'primaryButtonColorHex': '#2AA7E3', 'primaryTextColorHex': '#000000', 'secondaryButtonColorHex': '#CAE9F8', 'secondaryTextColorHex': '#000000' },
    { 'name': 'blue', 'primaryButtonColorHex': '#3F71F4', 'primaryTextColorHex': '#000000', 'secondaryButtonColorHex': '#CFDCFC', 'secondaryTextColorHex': '#000000' },
    { 'name': 'deep-purple', 'primaryButtonColorHex': '#6A4FF7', 'primaryTextColorHex': '#FFFFFF', 'secondaryButtonColorHex': '#DAD3FD', 'secondaryTextColorHex': '#000000' },
    { 'name': 'purple', 'primaryButtonColorHex': '#9346E9', 'primaryTextColorHex': '#FFFFFF', 'secondaryButtonColorHex': '#E4D1FA', 'secondaryTextColorHex': '#000000' },
    { 'name': 'light-purple', 'primaryButtonColorHex': '#CC63D6', 'primaryTextColorHex': '#000000', 'secondaryButtonColorHex': '#F2D8F5', 'secondaryTextColorHex': '#000000' },
    { 'name': 'deep-pink', 'primaryButtonColorHex': '#D8549A', 'primaryTextColorHex': '#000000', 'secondaryButtonColorHex': '#F5D4E6', 'secondaryTextColorHex': '#000000' },
    { 'name': 'pink', 'primaryButtonColorHex': '#FE8AA8', 'primaryTextColorHex': '#000000', 'secondaryButtonColorHex': '#FFE2E9', 'secondaryTextColorHex': '#000000' },
    { 'name': 'deep-red', 'primaryButtonColorHex': '#C52852', 'primaryTextColorHex': '#FFFFFF', 'secondaryButtonColorHex': '#F1C9D4', 'secondaryTextColorHex': '#000000' },
    { 'name': 'deep-orange', 'primaryButtonColorHex': '#D94C3E', 'primaryTextColorHex': '#000000', 'secondaryButtonColorHex': '#F6D2CF', 'secondaryTextColorHex': '#000000' },
    { 'name': 'orange', 'primaryButtonColorHex': '#EA9345', 'primaryTextColorHex': '#000000', 'secondaryButtonColorHex': '#FAE4D0', 'secondaryTextColorHex': '#000000' },
    { 'name': 'yellow', 'primaryButtonColorHex': '#EED052', 'primaryTextColorHex': '#000000', 'secondaryButtonColorHex': '#FBF3D4', 'secondaryTextColorHex': '#000000' },
    { 'name': 'light-green', 'primaryButtonColorHex': '#9FC929', 'primaryTextColorHex': '#000000', 'secondaryButtonColorHex': '#E7F2C9', 'secondaryTextColorHex': '#000000' },
    { 'name': 'green', 'primaryButtonColorHex': '#7FCF79', 'primaryTextColorHex': '#000000', 'secondaryButtonColorHex': '#DFF3DE', 'secondaryTextColorHex': '#000000' },
    { 'name': 'teal', 'primaryButtonColorHex': '#66C2AC', 'primaryTextColorHex': '#000000', 'secondaryButtonColorHex': '#D9F0EA', 'secondaryTextColorHex': '#000000' },
    { 'name': 'grey', 'primaryButtonColorHex': '#8A979E', 'primaryTextColorHex': '#000000', 'secondaryButtonColorHex': '#E2E5E7', 'secondaryTextColorHex': '#000000' },
    { 'name': 'deep-grey', 'primaryButtonColorHex': '#252829', 'primaryTextColorHex': '#FFFFFF', 'secondaryButtonColorHex': '#C8C9C9', 'secondaryTextColorHex': '#000000' }
  ];
  forms: any[] = [
    {
      'name': 'Login',
      'description': 'Login page to authenticate users',
      'template': 'LOGIN',
      'icon': 'account_box',
      'enabled': true
    },
    {
      'name': 'Identifier-first login',
      'description': 'Identifier-first login page to authenticate users',
      'template': 'IDENTIFIER_FIRST_LOGIN',
      'icon': 'account_box',
      'enabled': true
    },
    {
      'name': 'WebAuthn Login',
      'description': 'Passwordless page to authenticate users',
      'template': 'WEBAUTHN_LOGIN',
      'icon': 'fingerprint',
      'enabled': true
    },
    {
      'name': 'WebAuthn Register',
      'description': 'Passwordless page to register authenticators (devices)',
      'template': 'WEBAUTHN_REGISTER',
      'icon': 'fingerprint',
      'enabled': true
    },
    {
      'name': 'Registration',
      'description': 'Registration page to create an account',
      'template': 'REGISTRATION',
      'icon': 'person_add',
      'enabled': true
    },
    {
      'name': 'Registration confirmation',
      'description': 'Register page to confirm user account',
      'template': 'REGISTRATION_CONFIRMATION',
      'icon': 'how_to_reg',
      'enabled': true
    },
    {
      'name': 'Forgot password',
      'description': 'Forgot password to recover account',
      'template': 'FORGOT_PASSWORD',
      'icon': 'lock',
      'enabled': true
    },
    {
      'name': 'Reset password',
      'description': 'Reset password page to make a new password',
      'template': 'RESET_PASSWORD',
      'icon': 'lock_open',
      'enabled': true
    },
    {
      'name': 'User consent',
      'description': 'User consent to acknowledge and accept data access',
      'template': 'OAUTH2_USER_CONSENT',
      'icon': 'playlist_add_check',
      'enabled': true
    },
    {
      'name': 'MFA Enroll',
      'description': 'Multi-factor authentication settings page',
      'template': 'MFA_ENROLL',
      'icon': 'rotate_right',
      'enabled': true
    },
    {
      'name': 'MFA Challenge',
      'description': 'Multi-factor authentication verify page',
      'template': 'MFA_CHALLENGE',
      'icon': 'check_circle_outline',
      'enabled': true
    },
    {
      'name': 'MFA Challenge alternatives',
      'description': 'Multi-factor authentication alternatives page',
      'template': 'MFA_CHALLENGE_ALTERNATIVES',
      'icon': 'swap_horiz',
      'enabled': true
    },
    {
      'name': 'Recovery Codes',
      'description': 'Multi-factor authentication recovery code page',
      'template': 'MFA_RECOVERY_CODE',
      'icon': 'autorenew',
      'enabled': true
    },
    {
      'name': 'Error',
      'description': 'Error page to display a message describing the problem',
      'template': 'ERROR',
      'icon': 'error_outline',
      'enabled': true
    }
  ];
  selectedColorPalette: string;
  selectedTemplate = 'LOGIN';
  selectedMode = 'VIEW';
  config: any = {lineNumbers: true};
  formChanged: boolean;
  previewedTemplateContent: string;
  selectedTemplateContent: string;
  selectedTemplateName: string;
  canDeleteForm: boolean;
  formFound: boolean;
  private originalTemplateContent: string;
  private selectedForm: any;
  private preview: ElementRef;

  @ViewChild('preview') set content(content: ElementRef) {
    if (content) { // initially setter gets called with undefined
      this.preview = content;
      setTimeout(() => {
        this.refreshPreview();
        this.resizeIframe();
      }, 500);
    }
  }

  constructor(private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute,
              private authService: AuthService,
              private themeService: ThemeService,
              private dialogService: DialogService,
              private formService: FormService) {
  }

  ngOnInit() {
    this.envId = this.route.snapshot.params['envHrid'];
    this.domain = this.route.snapshot.data['domain'];
    this.themes = this.route.snapshot.data['themes'] || [];
    this.theme = this.themes[0] || {};
    if (this.theme.id && this.theme.primaryButtonColorHex) {
      const filteredObj = _.find(this.colorPalettes, {'primaryButtonColorHex': this.theme.primaryButtonColorHex});
      if (filteredObj) {
        this.selectedColorPalette = filteredObj.name;
      }
    }
    if (this.theme.logoWidth !== undefined && this.theme.logoWidth === 0) {
      this.theme.logoWidth = null;
    }
    this.readonly = !this.authService.hasPermissions(['domain_theme_update']);
    this.canDeleteForm = this.authService.hasPermissions(['domain_form_delete']);
    this.loadForm();
  }

  onModelTemplateChange(event) {
    if (this.selectedTemplateContent !== this.originalTemplateContent) {
      this.formChanged = true;
    }
  }

  onThemeChange(event) {
    this.formChanged = true;
    if (this.selectedMode === 'VIEW') {
      this.renderPreview();
    }
  }

  onSelectionTemplateChange(event) {
    if (this.selectedTemplateContent === this.originalTemplateContent) {
      this.loadForm();
    } else {
      this.dialogService
        .confirm('Pending changes', 'Are you sure you want to undo your pending changes?')
        .subscribe(res => {
          if (res) {
            this.loadForm();
          }
        });
    }
  }

  onModeChange(event) {
    if (event.value === 'VIEW') {
      this.renderPreview();
    }
  }

  isFormEnabled() {
    return this.selectedForm && this.selectedForm.enabled;
  }

  enableForm(event) {
    this.selectedForm.enabled = event.checked;
    this.formChanged = true;
  }

  deleteForm(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete form', 'Are you sure you want to delete this form ?')
      .subscribe(res => {
        if (res) {
          this.formService.delete(this.domain.id, null, this.selectedForm.id, false).subscribe(response => {
            this.snackbarService.open('Form deleted');
            this.loadForm();
            if (this.selectedMode === 'VIEW') {
              this.renderPreview();
            }
          });
        }
      });
  }

  reset() {
    this.dialogService
      .confirm('Reset Theme', 'Are you sure you want to reset the theme?')
      .subscribe(res => {
        if (res) {
          if (this.theme.id) {
            this.themeService.delete(this.domain.id, this.theme.id).subscribe(() => {
              this.snackbarService.open('Theme reset');
              this.themes = [];
              this.theme = {};
              this.selectedColorPalette = null;
              this.renderPreview();
            });
          } else {
            // if theme is missing, reseting the theme should also reset the unpublished fields
            this.themes = [];
            this.theme = {};
            this.selectedColorPalette = null;
            this.renderPreview();
          }
        }
      });
  }

  publish() {
    const themeToPublish: any = this.createThemeToPublish();
    const publishAction = (!this.theme.id) ?
      this.themeService.create(this.domain.id, themeToPublish) :
      this.themeService.update(this.domain.id, this.theme.id, themeToPublish);


    let formAction = of({});
    // only save page if there is content
    if (this.selectedTemplateContent) {
      this.selectedForm.content = this.selectedTemplateContent;
      formAction = (!this.selectedForm.id) ?
        this.formService.create(this.domain.id, null, this.selectedForm, false) :
        this.formService.update(this.domain.id, null, this.selectedForm.id, this.selectedForm, false);
    }

    forkJoin([publishAction, formAction]).subscribe(() => {
      this.snackbarService.open('Theme published');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { 'reload': true }});
      this.formChanged = false;
      this.loadTheme();
      this.loadForm();
    });
  }

  private createThemeToPublish() {
    const themeToPublish: any = {};
    themeToPublish.logoUrl = this.theme.logoUrl;
    themeToPublish.logoWidth = this.theme.logoWidth;
    themeToPublish.faviconUrl = this.theme.faviconUrl;
    themeToPublish.css = this.theme.css;
    if (this.selectedColorPalette) {
      const filteredObj = _.find(this.colorPalettes, { 'name' : this.selectedColorPalette });
      if (filteredObj) {
        themeToPublish.primaryButtonColorHex = filteredObj.primaryButtonColorHex;
        themeToPublish.secondaryButtonColorHex = filteredObj.secondaryButtonColorHex;
        themeToPublish.primaryTextColorHex = filteredObj.primaryTextColorHex;
        themeToPublish.secondaryTextColorHex = filteredObj.secondaryTextColorHex;
      }
    }
    return themeToPublish;
  }

  renderPreview() {
    const payload = {
      content: this.selectedForm.enabled ? this.selectedTemplateContent : null,
      theme: this.createThemeToPublish(),
      type: "FORM",
      template: this.selectedForm.template
    };

    this.formService.preview(this.domain.id, payload).subscribe(form => {
      this.previewedTemplateContent = form.content;
      this.refreshPreview();
    });
  }


  private fixAssetUrl(doc: any, tag: string, urlAttribute: string) {
    const tags = doc.getElementsByTagName(tag);
    for (let i = 0; i < tags.length; i++) {
      for (const attribute of tags[i].attributes) {
        if(attribute.name === urlAttribute){
          const url = attribute.value;
          if (!url.startsWith("http")) {
            attribute.value = "/" + url;
          }
        }
      }
    }
  }

  private refreshPreview() {
    if (this.previewedTemplateContent && this.preview) {
      const doc = this.preview.nativeElement.contentDocument || this.preview.nativeElement.contentWindow;
      if (doc) {
        doc.documentElement.innerHTML = this.previewedTemplateContent;
        this.fixAssetUrl(doc, "link", "href");
        this.fixAssetUrl(doc, "img", "src");
        this.fixAssetUrl(doc, "script", "src");
      }
    }
  }

  private resizeIframe() {
    if (this.selectedTemplateContent && this.preview) {
      const height = window.innerHeight * 0.80;
      this.preview.nativeElement.style.height = height + 'px';
    }
  }

  private loadForm() {
    this.formService.get(this.domain.id, null, this.selectedTemplate).subscribe(form => {
      this.selectedForm = form;
      this.formFound = this.selectedForm.id !== undefined;
      this.selectedTemplateContent = form.content;
      this.selectedTemplateName = this.selectedTemplate.toLowerCase().replace(/_/g, ' ');
      this.originalTemplateContent = this.selectedTemplateContent ? (' ' + this.selectedTemplateContent).slice(1) : this.selectedTemplateContent;
      setTimeout(() => {
        this.refreshPreview();
        this.resizeIframe();
      }, 500);
      this.renderPreview();
    });
  }

  private loadTheme() {
    this.themeService.getAll(this.domain.id).subscribe(themes => {
      this.themes = themes || [];
      this.theme = themes[0] || {};
    })
  }
}

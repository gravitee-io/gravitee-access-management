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
import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { filter, switchMap, tap } from 'rxjs/operators';
import { find } from 'lodash';

import { SnackbarService } from '../../../services/snackbar.service';
import { AuthService } from '../../../services/auth.service';
import { ThemeService } from '../../../services/theme.service';
import { DialogService } from '../../../services/dialog.service';
import { FormService } from '../../../services/form.service';
import { FormTemplateFactoryService } from '../../../services/form.template.factory.service';

@Component({
  selector: 'app-theme',
  templateUrl: './theme.component.html',
  styleUrls: ['./theme.component.scss'],
  standalone: false,
})
export class DomainSettingsThemeComponent implements OnInit {
  private envId: string;
  domain: any = {};
  readonly = false;
  themes: any[];
  theme: any;
  forms: any[];
  colorPalettes: any[] = [
    {
      name: 'light-blue',
      primaryButtonColorHex: '#2AA7E3',
      primaryTextColorHex: '#000000',
      secondaryButtonColorHex: '#CAE9F8',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'blue',
      primaryButtonColorHex: '#3F71F4',
      primaryTextColorHex: '#000000',
      secondaryButtonColorHex: '#CFDCFC',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'deep-purple',
      primaryButtonColorHex: '#6A4FF7',
      primaryTextColorHex: '#FFFFFF',
      secondaryButtonColorHex: '#DAD3FD',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'purple',
      primaryButtonColorHex: '#9346E9',
      primaryTextColorHex: '#FFFFFF',
      secondaryButtonColorHex: '#E4D1FA',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'light-purple',
      primaryButtonColorHex: '#CC63D6',
      primaryTextColorHex: '#000000',
      secondaryButtonColorHex: '#F2D8F5',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'deep-pink',
      primaryButtonColorHex: '#D8549A',
      primaryTextColorHex: '#000000',
      secondaryButtonColorHex: '#F5D4E6',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'pink',
      primaryButtonColorHex: '#FE8AA8',
      primaryTextColorHex: '#000000',
      secondaryButtonColorHex: '#FFE2E9',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'deep-red',
      primaryButtonColorHex: '#C52852',
      primaryTextColorHex: '#FFFFFF',
      secondaryButtonColorHex: '#F1C9D4',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'deep-orange',
      primaryButtonColorHex: '#D94C3E',
      primaryTextColorHex: '#000000',
      secondaryButtonColorHex: '#F6D2CF',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'orange',
      primaryButtonColorHex: '#EA9345',
      primaryTextColorHex: '#000000',
      secondaryButtonColorHex: '#FAE4D0',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'yellow',
      primaryButtonColorHex: '#EED052',
      primaryTextColorHex: '#000000',
      secondaryButtonColorHex: '#FBF3D4',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'light-green',
      primaryButtonColorHex: '#9FC929',
      primaryTextColorHex: '#000000',
      secondaryButtonColorHex: '#E7F2C9',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'green',
      primaryButtonColorHex: '#7FCF79',
      primaryTextColorHex: '#000000',
      secondaryButtonColorHex: '#DFF3DE',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'teal',
      primaryButtonColorHex: '#66C2AC',
      primaryTextColorHex: '#000000',
      secondaryButtonColorHex: '#D9F0EA',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'grey',
      primaryButtonColorHex: '#8A979E',
      primaryTextColorHex: '#000000',
      secondaryButtonColorHex: '#E2E5E7',
      secondaryTextColorHex: '#000000',
    },
    {
      name: 'deep-grey',
      primaryButtonColorHex: '#252829',
      primaryTextColorHex: '#FFFFFF',
      secondaryButtonColorHex: '#C8C9C9',
      secondaryTextColorHex: '#000000',
    },
  ];
  selectedColorPalette: string;
  selectedTemplate = 'LOGIN';
  selectedMode = 'VIEW';
  config: any = { lineNumbers: true };
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
    if (content) {
      // initially setter gets called with undefined
      this.preview = content;
      setTimeout(() => {
        this.refreshPreview();
        this.resizeIframe();
      }, 500);
    }
  }

  constructor(
    private snackbarService: SnackbarService,
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService,
    private themeService: ThemeService,
    private dialogService: DialogService,
    private formService: FormService,
    private formTemplateFactoryService: FormTemplateFactoryService,
  ) {}

  ngOnInit() {
    this.envId = this.route.snapshot.params['envHrid'];
    this.domain = this.route.snapshot.data['domain'];
    this.themes = this.route.snapshot.data['themes'] || [];
    this.theme = this.themes[0] || {};

    this.forms = this.formTemplateFactoryService
      .findAll()
      .filter((form) => form.template !== 'MAGIC_LINK_LOGIN' || this.allowMagicLink())
      .map((form) => {
        form.enabled = true;
        return form;
      });

    if (this.theme.id && this.theme.primaryButtonColorHex) {
      const filteredObj = find(this.colorPalettes, { primaryButtonColorHex: this.theme.primaryButtonColorHex });
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

  onModelTemplateChange() {
    if (this.selectedTemplateContent !== this.originalTemplateContent) {
      this.formChanged = true;
    }
  }

  onThemeChange() {
    this.formChanged = true;
    if (this.selectedMode === 'VIEW') {
      this.renderPreview();
    }
  }

  onSelectionTemplateChange() {
    if (this.selectedTemplateContent === this.originalTemplateContent) {
      this.loadForm();
    } else {
      this.dialogService.confirm('Pending changes', 'Are you sure you want to undo your pending changes?').subscribe((res) => {
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

  isFormEnabled(): boolean {
    return this.selectedForm?.enabled;
  }

  enableForm(event) {
    this.selectedForm.enabled = event.checked;
    this.formChanged = true;
  }

  deleteForm(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete form', 'Are you sure you want to delete this form ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.formService.delete(this.domain.id, null, this.selectedForm.id, false)),
        tap(() => {
          this.snackbarService.open('Form deleted');
          this.loadForm();
          if (this.selectedMode === 'VIEW') {
            this.renderPreview();
          }
        }),
      )
      .subscribe();
  }

  reset() {
    this.dialogService
      .confirm('Reset Theme', 'Are you sure you want to reset the theme?')
      .pipe(
        filter((res) => res),
        switchMap(() => {
          if (this.theme.id) {
            return this.themeService.delete(this.domain.id, this.theme.id);
          } else {
            return of(false);
          }
        }),
        tap(() => {
          if (this.theme.id) {
            this.snackbarService.open('Theme reset');
          }
          this.themes = [];
          this.theme = {};
          this.selectedColorPalette = null;
          this.renderPreview();
        }),
      )
      .subscribe();
  }

  publish() {
    const themeToPublish: any = this.createThemeToPublish();
    const publishAction = !this.theme.id
      ? this.themeService.create(this.domain.id, themeToPublish)
      : this.themeService.update(this.domain.id, this.theme.id, themeToPublish);

    let formAction = of({});
    // only save page if there is content
    if (this.selectedTemplateContent) {
      this.selectedForm.content = this.selectedTemplateContent;
      formAction = !this.selectedForm.id
        ? this.formService.create(this.domain.id, null, this.selectedForm, false)
        : this.formService.update(this.domain.id, null, this.selectedForm.id, this.selectedForm, false);
    }

    forkJoin([publishAction, formAction]).subscribe(() => {
      this.snackbarService.open('Theme published');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
      this.formChanged = false;
      this.loadTheme();
      this.loadForm();
    });
  }

  private allowMagicLink(): boolean {
    return this.domain.loginSettings?.magicLinkAuthEnabled;
  }

  private createThemeToPublish() {
    const themeToPublish: any = {};
    themeToPublish.logoUrl = this.theme.logoUrl;
    themeToPublish.logoWidth = this.theme.logoWidth;
    themeToPublish.faviconUrl = this.theme.faviconUrl;
    themeToPublish.css = this.theme.css;
    if (this.selectedColorPalette) {
      const filteredObj = find(this.colorPalettes, { name: this.selectedColorPalette });
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
      type: 'FORM',
      template: this.selectedForm.template,
    };

    this.formService.preview(this.domain.id, payload).subscribe((form) => {
      this.previewedTemplateContent = form.content;
      this.refreshPreview();
    });
  }

  private refreshPreview() {
    if (this.previewedTemplateContent && this.preview) {
      const doc = this.preview.nativeElement.contentDocument || this.preview.nativeElement.contentWindow;
      if (doc) {
        doc.documentElement.innerHTML = this.previewedTemplateContent;
      }
    }
  }

  private resizeIframe() {
    if (this.selectedTemplateContent && this.preview) {
      const height = window.innerHeight * 0.8 + 22; /* correction for padding container */
      this.preview.nativeElement.style.height = height + 'px';
    }
  }

  private loadForm() {
    this.formService.get(this.domain.id, null, this.selectedTemplate).subscribe((form) => {
      this.selectedForm = form;
      this.formFound = this.selectedForm.id !== undefined;
      this.selectedTemplateContent = form.content;
      this.selectedTemplateName = this.selectedTemplate.toLowerCase().replace(/_/g, ' ');
      this.originalTemplateContent = this.selectedTemplateContent
        ? (' ' + this.selectedTemplateContent).slice(1)
        : this.selectedTemplateContent;
      setTimeout(() => {
        this.refreshPreview();
        this.resizeIframe();
      }, 500);
      this.renderPreview();
    });
  }

  private loadTheme() {
    this.themeService.getAll(this.domain.id).subscribe((themes) => {
      this.themes = themes || [];
      this.theme = themes[0] || {};
    });
  }
}

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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {SnackbarService} from '../../../services/snackbar.service';
import {AuthService} from '../../../services/auth.service';
import {ThemeService} from '../../../services/theme.service';
import {DialogService} from '../../../services/dialog.service';
import * as _ from 'lodash';

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
  selectedColorPalette: string;
  config: any = { lineNumbers: true };
  formChanged: boolean;

  constructor(private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute,
              private authService: AuthService,
              private themeService: ThemeService,
              private dialogService: DialogService) {
  }

  ngOnInit() {
    this.envId = this.route.snapshot.params['envHrid'];
    this.domain = this.route.snapshot.data['domain'];
    this.themes =  this.route.snapshot.data['themes'] || [];
    this.theme = this.themes[0] || {};
    if (this.theme.id && this.theme.primaryButtonColorHex) {
      const filteredObj = _.find(this.colorPalettes, { 'primaryButtonColorHex' : this.theme.primaryButtonColorHex });
      if (filteredObj) {
        this.selectedColorPalette = filteredObj.name;
      }
    }
    if (this.theme.logoWidth !== undefined && this.theme.logoWidth === 0) {
      this.theme.logoWidth = null;
    }
    this.readonly = !this.authService.hasPermissions(['domain_theme_update']);
  }

  onModelChange(event) {
    this.formChanged = true;
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
            });
          }
        }
      });
  }

  publish() {
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
    const publishAction = (!this.theme.id) ?
      this.themeService.create(this.domain.id, themeToPublish) :
      this.themeService.update(this.domain.id, this.theme.id, themeToPublish);

    publishAction.subscribe(() => {
      this.snackbarService.open('Theme published');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { 'reload': true }});
      this.formChanged = false;
    });
  }
}

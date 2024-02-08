module.exports = {
  plugins: ['stylelint-order'],
  extends: ['stylelint-config-standard-scss', 'stylelint-config-idiomatic-order', 'stylelint-config-prettier-scss'],
  rules: {
    'scss/dollar-variable-pattern': null,
    'selector-class-pattern': null,
    'selector-pseudo-element-no-unknown': [
      true,
      {
        ignorePseudoElements: ['ng-deep'],
      },
    ],
    'custom-property-pattern': ['-.*', '--.*'],
    'no-empty-source': null,
  },
};

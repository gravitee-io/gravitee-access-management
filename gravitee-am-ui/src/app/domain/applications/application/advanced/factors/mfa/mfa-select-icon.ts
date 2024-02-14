enum MfaSelectIcon {
  OTP = 'mobile_friendly',
  SMS = 'sms',
  EMAIL = 'email',
  CALL = 'call',
  HTTP = 'http',
  RECOVERY_CODE = 'autorenew',
  FIDO2 = 'fingerprint',
}

enum MfaType {
  OTP = 'TOTP',
  SMS = 'SMS',
  EMAIL = 'EMAIL',
  CALL = 'CALL',
  HTTP = 'HTTP',
  RECOVERY_CODE = 'Recovery Code',
  FIDO2 = 'FIDO2',
}

export function getFactorTypeIcon(type: string): string {
  const factorType = type.toUpperCase();
  if (MfaSelectIcon[factorType]) {
    return MfaSelectIcon[factorType];
  } else {
    return 'donut_large';
  }
}

export function getDisplayFactorType(type: string): string {
  const factorType = type.toUpperCase();
  if (MfaType[factorType]) {
    return MfaType[factorType];
  } else {
    return 'Custom';
  }
}

function loadFingerprintJsV3Gravitee(callback) {
    FingerprintJS.load().then(fp => fp.get()).then(callback);
}


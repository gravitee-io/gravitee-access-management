function loadFingerprintJsV3Pro(browserKey, region, promise) {
    const creds = {
        token: browserKey,
    };
    if (region && region !== ""){
        creds.region = region;
    }
    new Promise((resolve, reject) => {
        const script = document.createElement('script')
        script.onload = resolve
        script.onerror = reject
        script.async = true
        script.src = 'https://cdn.jsdelivr.net/npm/@fingerprintjs/fingerprintjs-pro@3/dist/fp.min.js'
        document.head.appendChild(script)
    }).then(() => FingerprintJS.load(creds))
        .then(fp => fp.get())
        .then(promise)
}


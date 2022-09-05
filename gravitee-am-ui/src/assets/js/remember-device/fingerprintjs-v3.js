function loadFingerprintJsV3(promise) {
    const fpPromise = new Promise((resolve, reject) => {
        const script = document.createElement('script')
        script.onload = resolve
        script.onerror = reject
        script.async = true
        script.src = 'https://cdn.jsdelivr.net/npm/@fingerprintjs/fingerprintjs@3/dist/fp.min.js'
        document.head.appendChild(script)
    })
        .then(() => FingerprintJS.load())
        .then(fp => fp.get())
        .then(promise)
}


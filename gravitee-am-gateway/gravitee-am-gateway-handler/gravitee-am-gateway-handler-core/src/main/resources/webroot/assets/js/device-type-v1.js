// this script provides the platform as seen by the browser

function retrievePlatform(navigator) {
    let platform = "unknown";
    if (navigator?.userAgentData) {
        platform = navigator.userAgentData.platform
    } else if (navigator?.platform) {
        platform = window.navigator.platform
    }
    return platform;
}
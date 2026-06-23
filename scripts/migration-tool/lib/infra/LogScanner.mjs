/**
 * LogScanner — find ERROR-level lines in captured process/pod stdout.
 *
 * The standalone Gateway/MAPI logback pattern is:
 *   %d{HH:mm:ss.SSS} [%thread] [%X{domain}] %-5level %logger{36} - %msg
 * so an ERROR line contains the level token ` ERROR `. The regex below matches that token
 * standalone (tolerant of the `kubectl logs --prefix` pod tag prepended to each line) and
 * also catches JSON logging's `"level":"ERROR"` shape, should pods ever switch format.
 */
const ERROR_LINE = /(^|\s)ERROR(\s|$)/;

/**
 * Scan text for ERROR lines.
 * @param {string} text - combined stdout to scan
 * @param {string} [source] - label for where the text came from (e.g. release/pod name)
 * @returns {{ source: string, count: number, lines: string[] }}
 */
export function scanForErrors(text, source = '') {
    const lines = (text || '').split('\n');
    const hits = lines.filter((l) => ERROR_LINE.test(l)).map((l) => l.trim());
    return { source, count: hits.length, lines: hits };
}

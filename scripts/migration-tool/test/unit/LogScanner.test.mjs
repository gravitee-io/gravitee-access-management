import { scanForErrors } from '../../lib/infra/LogScanner.mjs';

/**
 * Unit tests for the ERROR-line scanner used to surface Gateway/MAPI stdout errors.
 */
describe('LogScanner.scanForErrors', () => {
    const errorBlock = [
        '10:23:45.123 [vert.x-eventloop-thread-1] [domain-1] INFO  i.g.a.g.Handler - started',
        '10:23:46.001 [vert.x-eventloop-thread-1] [domain-1] WARN  i.g.a.g.Handler - slow response',
        '10:23:47.456 [vert.x-eventloop-thread-1] [domain-1] ERROR i.g.a.g.Handler - NullPointerException',
        '\tat io.gravitee.am.gateway.Handler.handle(Handler.java:42)'
    ].join('\n');

    test('finds a single ERROR line in a logback block', () => {
        const { count, lines } = scanForErrors(errorBlock, 'am-gateway-dp1');
        expect(count).toBe(1);
        expect(lines[0]).toContain('ERROR i.g.a.g.Handler - NullPointerException');
    });

    test('returns the source label', () => {
        const { source } = scanForErrors(errorBlock, 'am-gateway-dp1');
        expect(source).toBe('am-gateway-dp1');
    });

    test('returns zero for a clean INFO/WARN block', () => {
        const clean = [
            '10:23:45.123 [thread] [domain-1] INFO  i.g.a.Boot - ready',
            '10:23:46.001 [thread] [domain-1] WARN  i.g.a.Boot - deprecated config',
            '10:23:46.500 [thread] [domain-1] DEBUG i.g.a.Boot - ERRORS=0 retries' // "ERRORS" must not match
        ].join('\n');
        const { count } = scanForErrors(clean, 'am-mapi');
        expect(count).toBe(0);
    });

    test('matches even with a kubectl --prefix pod tag prepended', () => {
        const prefixed = '[pod/am-gateway-dp1-abc/gateway] 10:23:47.456 [t] [d] ERROR i.g.a.X - boom';
        const { count } = scanForErrors(prefixed);
        expect(count).toBe(1);
    });

    test('counts multiple ERROR lines', () => {
        const text = errorBlock + '\n10:24:00.000 [t] [d] ERROR i.g.a.Y - second failure';
        const { count } = scanForErrors(text);
        expect(count).toBe(2);
    });

    test('handles empty / nullish input safely', () => {
        expect(scanForErrors('').count).toBe(0);
        expect(scanForErrors(undefined).count).toBe(0);
        expect(scanForErrors(null).count).toBe(0);
    });
});

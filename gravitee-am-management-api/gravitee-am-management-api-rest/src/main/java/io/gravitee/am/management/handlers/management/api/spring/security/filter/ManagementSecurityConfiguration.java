package io.gravitee.am.management.handlers.management.api.spring.security.filter;

import io.gravitee.am.management.handlers.management.api.authentication.csrf.CookieCsrfSignedTokenRepository;
import io.gravitee.am.management.handlers.management.api.authentication.filter.JWTAuthenticationFilter;
import io.gravitee.am.management.handlers.management.api.authentication.web.Http401UnauthorizedEntryPoint;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static io.gravitee.am.management.handlers.management.api.spring.security.SecurityConfiguration.*;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.joining;

@Configuration
public class ManagementSecurityConfiguration extends CsrfAwareConfiguration {

    private static final String[] PATHS = {"/organizations/**", "/user/**", "/platform/**"};
    private final Http401UnauthorizedEntryPoint http401UnauthorizedEntryPoint;

    @Autowired
    public ManagementSecurityConfiguration(
            Environment environment,
            Http401UnauthorizedEntryPoint http401UnauthorizedEntryPoint) {
        super(environment);
        this.http401UnauthorizedEntryPoint = http401UnauthorizedEntryPoint;
    }

    @Bean
    @Order(102)
    public SecurityFilterChain managementSecurityFilter(
            HttpSecurity http,
            JWTAuthenticationFilter jwtAuthenticationFilter,
            CookieCsrfSignedTokenRepository csrfSignedTokenRepository
    ) throws Exception {
        http.authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests.requestMatchers(PATHS).authenticated())
                .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> {
                })
                .httpBasic(httpBasic -> httpBasic.disable())
                .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(http401UnauthorizedEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return applyCsrf(csp(http), csrfSignedTokenRepository).build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/swagger.json");
    }

    private HttpSecurity csp(HttpSecurity http) throws Exception {
        final Boolean cspIsEnabled = environment.getProperty(HTTP_CSP_ENABLED, Boolean.class, true);
        if (cspIsEnabled) {
            final List<String> directives = getDirectives();
            if (directives.isEmpty()) {
                directives.add(DEFAULT_DEFAULT_SRC_CSP_DIRECTIVE);
                directives.add(DEFAULT_FRAME_ANCESTOR_CSP_DIRECTIVE);
            }

            return http.headers(headers ->
                    headers.contentSecurityPolicy(sp ->
                            sp.policyDirectives(getPolicyDirectives(directives))
                    )
            );
        }
        return http;
    }

    private static String getPolicyDirectives(List<String> directives) {
        return directives.stream()
                .map(directive -> directive.trim().endsWith(";") ? directive : directive + ";")
                .collect(joining(" "));
    }

    private List<String> getDirectives() {
        final List<String> directives = new ArrayList<>();
        String value;
        int i = 0;
        while (nonNull(value = getProperty(i))) {
            directives.add(value);
            i++;
        }

        return directives;
    }

    private String getProperty(int i) {
        final String propertyKey = String.format(HTTP_CSP_DIRECTIVES, i);
        return environment.getProperty(propertyKey, String.class);
    }
}
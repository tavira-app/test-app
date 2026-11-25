package ir.khodroban.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.khodroban.identity.LocalIdentityFilter;
import ir.khodroban.shared.ApiError;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper,
                                            ObjectProvider<LocalIdentityFilter> localFilter,
                                            ObjectProvider<JwtDecoder> jwtDecoder)
            throws Exception {
        var chain = http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(mapper, request, response, 401,
                                        "AUTHENTICATION_REQUIRED", "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(mapper, request, response, 403,
                                        "ACCESS_DENIED", "Access to this resource is not permitted.")))
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        var decoder = jwtDecoder.getIfAvailable();
        if (decoder != null) {
            chain.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder)));
        }
        var local = localFilter.getIfAvailable();
        if (local != null) {
            chain.addFilterBefore(local, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        }
        return chain.build();
    }

    private void writeError(ObjectMapper mapper, HttpServletRequest request,
                            HttpServletResponse response, int status, String code, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiError("about:blank",
                status == 401 ? "Unauthorized" : "Forbidden", status, detail,
                request.getRequestURI(), code, UUID.randomUUID().toString(), List.of()));
    }
}

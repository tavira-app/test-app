package ir.khodroban.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Header-based identity is available only when the explicitly selected local profile is active. */
public class LocalIdentityFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String subject = request.getHeader("X-Khodroban-User");
        if (SecurityContextHolder.getContext().getAuthentication() == null
                && subject != null && !subject.isBlank() && subject.length() <= 200) {
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    subject, "local-profile", List.of());
            authentication.setDetails(request.getHeader("X-Khodroban-Email"));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }
}

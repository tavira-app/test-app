package ir.khodroban.identity;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalIdentityConfiguration {
    @Bean
    LocalIdentityFilter localIdentityFilter() {
        return new LocalIdentityFilter();
    }

    @Bean
    FilterRegistrationBean<LocalIdentityFilter> disableContainerRegistration(LocalIdentityFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}

package ir.khodroban.alerts;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "khodroban.alerts.enabled", havingValue = "true", matchIfMissing = true)
public class AlertConfiguration {
    @Bean
    AlertJob alertJob(AlertService service) {
        return new AlertJob(service);
    }

    @Bean
    DeliveryJob deliveryJob(AlertService service) {
        return new DeliveryJob(service);
    }

    static final class AlertJob {
        private final AlertService service;

        AlertJob(AlertService service) { this.service = service; }

        @Scheduled(fixedDelayString = "${khodroban.alerts.poll-delay-ms:60000}")
        public void poll() { service.generateAlerts(); }
    }

    static final class DeliveryJob {
        private final AlertService service;

        DeliveryJob(AlertService service) { this.service = service; }

        @Scheduled(fixedDelayString = "${khodroban.alerts.delivery-delay-ms:60000}")
        public void deliver() { service.deliverInApp(); }
    }
}

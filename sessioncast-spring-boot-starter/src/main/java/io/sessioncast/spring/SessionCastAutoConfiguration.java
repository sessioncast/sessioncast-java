package io.sessioncast.spring;

import io.sessioncast.core.SessionCastClient;
import io.sessioncast.core.tmux.TmuxController;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for SessionCast.
 */
@AutoConfiguration
@ConditionalOnClass(SessionCastClient.class)
@EnableConfigurationProperties(SessionCastProperties.class)
public class SessionCastAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SessionCastAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public TmuxController tmuxController() {
        return new TmuxController();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sessioncast.relay", name = "token")
    public SessionCastClient sessionCastClient(SessionCastProperties props, TmuxController tmuxController) {
        SessionCastProperties.Relay relay = props.getRelay();
        SessionCastProperties.Agent agent = props.getAgent();
        SessionCastProperties.Reconnect reconnect = props.getReconnect();

        return SessionCastClient.builder()
            .relay(relay.getUrl())
            .token(relay.getToken())
            .machineId(agent.getMachineId())
            .label(agent.getLabel())
            .reconnect(reconnect.isEnabled())
            .reconnectDelay(reconnect.getInitialDelay(), reconnect.getMaxDelay())
            .maxReconnectAttempts(reconnect.getMaxAttempts())
            .autoStreamOnCreate(agent.isAutoStreamOnCreate())
            .tmuxController(tmuxController)
            .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "sessioncast.agent", name = "auto-connect", havingValue = "true", matchIfMissing = true)
    public SessionCastClientLifecycle sessionCastClientLifecycle(SessionCastClient client) {
        return new SessionCastClientLifecycle(client);
    }

    /**
     * Lifecycle bean for auto-connect/disconnect.
     */
    public static class SessionCastClientLifecycle {

        private final SessionCastClient client;

        public SessionCastClientLifecycle(SessionCastClient client) {
            this.client = client;
        }

        @PostConstruct
        public void connect() {
            log.info("Auto-connecting SessionCast client...");
            client.connect()
                .thenRun(() -> log.info("SessionCast client connected"))
                .exceptionally(e -> {
                    log.error("Failed to connect SessionCast client: {}", e.getMessage());
                    return null;
                });
        }

        @PreDestroy
        public void disconnect() {
            log.info("Disconnecting SessionCast client...");
            client.close();
        }
    }
}

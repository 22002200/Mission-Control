package com.missioncontrol.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.missioncontrol.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

/**
 * Acceptance criterion 11 and NFR-4: no password, hash or token is ever written to a log.
 *
 * <p>Asserted rather than assumed, because this is the kind of thing that stays true until someone
 * adds a helpful {@code log.debug} to a failing login and nobody notices for a year.
 *
 * <p><strong>Scope.</strong> The appender is attached to {@code com.missioncontrol} and turned up
 * to TRACE - the application's own logging, at maximum verbosity, which is what this codebase
 * controls and what the requirement is about. It is deliberately not attached to the root logger
 * at TRACE: Spring MVC logs request bodies at that level, so doing so would assert that the
 * framework has no debugging facilities rather than that this application misuses none. The
 * practical consequence is a configuration rule instead of a code one - never raise
 * {@code org.springframework.web} to DEBUG or TRACE in a deployment - and {@code application.yml}
 * pins {@code org.springframework.security} to INFO for the same reason.
 */
class LogRedactionIT extends AbstractIntegrationTest {

    private static final String APPLICATION_LOGGER = "com.missioncontrol";

    private ListAppender<ILoggingEvent> appender;
    private Logger applicationLogger;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        applicationLogger = (Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        originalLevel = applicationLogger.getLevel();
        // Maximum verbosity for our own code: if any of it would write a credential at any level,
        // this is where that shows up, rather than the first time someone debugs production.
        applicationLogger.setLevel(Level.TRACE);

        appender = new ListAppender<>();
        appender.start();
        applicationLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        applicationLogger.detachAppender(appender);
        appender.stop();
        applicationLogger.setLevel(originalLevel);
    }

    private String captured() {
        StringBuilder text = new StringBuilder();
        List<ILoggingEvent> events = List.copyOf(appender.list);
        for (ILoggingEvent event : events) {
            text.append(event.getFormattedMessage()).append('\n');
            if (event.getArgumentArray() != null) {
                for (Object argument : event.getArgumentArray()) {
                    text.append(argument).append('\n');
                }
            }
            if (event.getThrowableProxy() != null) {
                text.append(event.getThrowableProxy().getMessage()).append('\n');
            }
        }
        return text.toString();
    }

    @Test
    @DisplayName("No password, hash or token appears in the logs")
    void credentialsNeverReachTheLog() throws Exception {
        String uniquePassword = "wrong-password-marker-8f3a2b";

        // A successful login, a failed one, a disabled one, an authenticated read and a logout -
        // every path that touches a credential.
        String token = tokenFor(LOG_TEST_CREW);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(LOG_TEST_CREW, uniquePassword)));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(DISABLED_USER, SEED_PASSWORD)));

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)));
        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)));

        String logged = captured();

        assertThat(logged)
                .as("the raw password submitted on a failed login")
                .doesNotContain(uniquePassword);
        assertThat(logged)
                .as("the seeded password")
                .doesNotContain(SEED_PASSWORD);
        assertThat(logged)
                .as("a BCrypt hash")
                .doesNotContain("$2a$");
        assertThat(logged)
                .as("the issued token")
                .doesNotContain(token);
    }
}

package com.missioncontrol.platform;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata.
 *
 * <p>The generated spec at {@code /v3/api-docs} is the contract the TypeScript client is built
 * from, so this is load-bearing rather than decorative: see {@code frontend/openapi-ts.config.ts}.
 *
 * <p>The {@code bearerAuth} scheme is <em>declared but not applied</em> to any operation, matching
 * the current state of {@link SecurityConfig}. When authentication lands, add
 * {@code .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))} here so the generated
 * client knows to attach the token.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI missionControlOpenApi(@Value("${missioncontrol.version}") String version) {
        return new OpenAPI()
                // Pinned to a relative URL on purpose. Left to itself springdoc derives the
                // server URL from whichever host the spec was fetched over, which bakes an
                // environment-specific address ("http://backend:8080" when generated from
                // inside Compose) into the committed TypeScript client.
                .servers(List.of(new Server().url("/").description("Relative to the current host")))
                .info(new Info()
                        .title("Mission Control API")
                        .description("Space mission planning and crew assignment.")
                        .version(version)
                        .license(new License().name("Proprietary")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Not yet enforced - see SecurityConfig TODO(auth).")));
    }
}

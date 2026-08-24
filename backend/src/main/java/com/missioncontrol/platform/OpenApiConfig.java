package com.missioncontrol.platform;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
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
 * <p>The {@code bearerAuth} scheme is applied globally, which is what makes the generated client
 * attach the token: {@code @hey-api/openapi-ts} emits a {@code security} array only for operations
 * the document marks as secured, and the fetch client consults its {@code auth} callback only when
 * that array is present. Login opts out with an empty {@code @SecurityRequirements}, so no stale
 * header is sent when signing in.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI missionControlOpenApi(@Value("${missioncontrol.version}") String version) {
        return new OpenAPI()
                // Pinned to a relative URL on purpose. Left to itself springdoc derives the
                // server URL from whichever host the spec was fetched over, which bakes an
                // environment-specific address ('http://backend:8080' when generated from
                // inside Compose) into the committed TypeScript client.
                .servers(List.of(new Server().url("/").description("Relative to the current host")))
                .info(new Info()
                        .title("Mission Control API")
                        .description("Space mission planning and crew assignment.")
                        .version(version)
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Obtain a token from POST /api/auth/login.")));
    }
}

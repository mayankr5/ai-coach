package com.aicoach.config;

import com.aicoach.common.MaybeNull;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Makes the OpenAPI spec precise enough to generate client types from: response fields are required unless
 * marked {@link MaybeNull}, which become nullable. Request bodies are left alone, as their fields really are
 * optional. Also declares the bearer token, so Swagger UI can call protected endpoints.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("AI Coach API").version("v1"))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    PropertyCustomizer maybeNullProperties() {
        return (property, type) -> {
            boolean maybeNull = type.getCtxAnnotations() != null
                    && Arrays.stream(type.getCtxAnnotations()).anyMatch(a -> a instanceof MaybeNull);
            if (!maybeNull) {
                return property;
            }
            if (property.get$ref() != null) {
                // A reference can't take another type alongside it: "one of the object, or null"
                Schema<Object> nullable = new Schema<>();
                nullable.setOneOf(List.of(new Schema<>().$ref(property.get$ref()), nullSchema()));
                return nullable;
            }
            property.addType("null");
            return property;
        };
    }

    @Bean
    OpenApiCustomizer requiredResponseFields() {
        return openApi -> openApi.getComponents().getSchemas().forEach((name, schema) -> {
            if (isResponse(name) && schema.getProperties() != null) {
                schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
            }
        });
    }

    /** Request bodies and Spring's paging parameters keep their optional fields. */
    private static boolean isResponse(String schemaName) {
        return !schemaName.endsWith("Request") && !schemaName.equals("TaskResult") && !schemaName.equals("Pageable");
    }

    private static Schema<Object> nullSchema() {
        Schema<Object> schema = new Schema<>();
        schema.addType("null");
        return schema;
    }
}

package com.example.bank_cards.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class SwaggerConfig {
    private static final String SECURITY_SCHEME_KEY = "BearerAuth";
    private static final String API_TITLE = "Bank cards Management API";
    private static final String API_VERSION = "1.0.0";
    private static final String API_DESCRIPTION = """
                                                  ## Полнофункциональное API для Системы Управления Банковскими Картами
        
                                                  Данное API предоставляет эндпоинты для комплексного управления банковскими картами, включая следующие функции:
                                                  *   **Регистрация и авторизация пользователей:** Создание аккаунтов, вход в систему.
                                                  *   **Операции с картами:** Проведение транзакций (снятие наличных, переводы между счетами, пополнение баланса).
                                                  *   **Создание и хранение карт:** Генерация новых карт с автоматическим шифрованием и безопасным хранением реквизитов.
                                                  *   **Управление лимитами:** Настройка дневных/месячных лимитов по операциям для каждой карты.
                                                  *   **Пагинация и валидация данных:** Постраничная выдача результатов и строгая проверка входящих данных.
        
                                                  API соответствует RESTful-принципам и использует JSON для запросов и ответов.
                                                  Для доступа к большинству эндпоинтов требуется аутентификация через Bearer Token (JWT).
        
                                                  **Ключевые особенности:**
                                                  - Полностью зашифрованное хранение данных карт
                                                  - Гибкая система лимитов
                                                  - Подробные логи всех операций
                                                  - Валидация данных на уровне API и бизнес-логики
        
                                                  *Для получения подробной информации о доступных методах, форматах запросов и кодах ошибок обратитесь к документации конкретных эндпоинтов.*
                                                  """;

    private static final String TERMS_OF_SERVICE_URL = "https://example.com/terms";
    private static final String CONTACT_NAME = "SeeXWH";
    private static final String CONTACT_URL = "https://github.com/SeeXWH/bank_cards";
    private static final String CONTACT_EMAIL = "andreymikhailov.vma@gmail.com";
    private static final String LICENSE_NAME = "Apache 2.0";
    private static final String LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0.html";
    private static final String EXTERNAL_DOCS_DESCRIPTION = "Detailed User Guide and Tutorials";
    private static final String EXTERNAL_DOCS_URL = "https://github.com/SeeXWH";
    private static final String SECURITY_SCHEME_DESCRIPTION = """
                                                                Данное API использует аутентификацию с помощью **Bearer Token** на основе **JSON Web Tokens (JWT)**. 
                                                                Для доступа к защищённым endpoint'ам необходимо сначала пройти аутентификацию (например, через `/api/users/login`), чтобы получить JWT-токен. 
                                                    
                                                                **Как использовать:** 
                                                                1. Аутентифицируйтесь, используя свои учётные данные, чтобы получить access-токен. 
                                                                2. Для последующих запросов к защищённым endpoint'ам добавьте HTTP-заголовок `Authorization`. 
                                                                3. Укажите в заголовке `Authorization` схему `Bearer`, а затем ваш токен. 
                                                                **Пример заголовка:** 
                                                                ```http 
                                                                Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c \s
                                                                ``` 
                                                                *Убедитесь, что токен корректно сформирован, не истёк и имеет необходимые права для запрашиваемой операции. Нажмите кнопку **Authorize** (или значок замка) ниже, чтобы глобально установить токен для тестирования запросов через Swagger UI.* \s
                                                                """;
    @Value("${swagger.servers:http://localhost:8080}")
    private String serverUrls;

    @Bean
    @NonNull
    public OpenAPI customOpenAPI() {

        return new OpenAPI()
                .info(apiInfo())
                .servers(createServerList())
                .externalDocs(apiExternalDocs())
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_KEY))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_KEY, createSecurityScheme())
                );
    }

    @NonNull
    private List<Server> createServerList() {
        if (!StringUtils.hasText(serverUrls)) {
            log.warn("Server URLs property ('swagger.servers') is blank or not provided. " +
                    "Using default server entry '/'. Documentation might not reflect actual deployment URLs.");
            return Collections.singletonList(new Server().url("/").description("Default Server (Relative Path)"));
        }

        List<Server> servers = Arrays.stream(serverUrls.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(url -> {
                    Server server = new Server().url(url);
                    if (url.contains("localhost") || url.contains("127.0.0.1")) {
                        server.description("Local Development Server");
                    } else if (url.contains("staging") || url.contains("stg") || url.contains(
                            "dev")) {
                        server.description("Staging/Development Environment");
                    } else if (url.contains("prod") || url.contains("production") || (!url.contains("localhost") && !url.contains("staging") && !url.contains("dev"))) {
                        server.description("Production Environment");
                    } else {
                        server.description("API Server");
                    }
                    log.trace("Created Server object: url='{}', description='{}'",
                            server.getUrl(), server.getDescription()
                    );
                    return server;
                })
                .collect(Collectors.toList());

        if (servers.isEmpty()) {
            log.warn("After parsing 'swagger.servers', no valid URLs were found. Using default server entry '/'.");
            return Collections.singletonList(new Server().url("/").description("Default Server (Relative Path)"));
        }

        log.debug(
                "Successfully created server list: {}",
                servers.stream().map(Server::getUrl).collect(Collectors.joining(", "))
        );
        return servers;
    }

    @NonNull
    private Info apiInfo() {
        return new Info()
                .title(API_TITLE)
                .version(API_VERSION)
                .description(API_DESCRIPTION)
                .termsOfService(TERMS_OF_SERVICE_URL)
                .contact(apiContact())
                .license(apiLicense());
    }

    @NonNull
    private Contact apiContact() {
        return new Contact()
                .name(CONTACT_NAME)
                .url(CONTACT_URL)
                .email(StringUtils.hasText(CONTACT_EMAIL) ? CONTACT_EMAIL : null);
    }

    @NonNull
    private License apiLicense() {
        return new License()
                .name(StringUtils.hasText(LICENSE_NAME) ? LICENSE_NAME : "Undefined")
                .url(LICENSE_URL);
    }

    @NonNull
    private SecurityScheme createSecurityScheme() {
        return new SecurityScheme()
                .name(SECURITY_SCHEME_KEY)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description(SECURITY_SCHEME_DESCRIPTION);
    }

    @NonNull
    private ExternalDocumentation apiExternalDocs() {
        return new ExternalDocumentation()
                .description(EXTERNAL_DOCS_DESCRIPTION)
                .url(EXTERNAL_DOCS_URL);
    }

}

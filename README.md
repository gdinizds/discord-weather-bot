# ⛅ Discord Weather Bot

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Oracle GraalVM](https://img.shields.io/badge/Oracle%20GraalVM%20(GFTC)-JDK%2025-red?logo=graalvm)](https://www.graalvm.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Open-Meteo](https://img.shields.io/badge/Open--Meteo-Weather%20API-blue)](https://open-meteo.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue?logo=postgresql)](https://www.postgresql.org/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka%20%2F%20Redpanda-Event--Driven-black?logo=apachekafka)](https://kafka.apache.org/)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-2.3.0-yellow)](https://resilience4j.readme.io/)

O **Discord Weather Bot** é um microsserviço corporativo de previsão meteorológica orientado a eventos para comunidades no Discord. Desenvolvido sobre o **Java 25 com Oracle GraalVM (GFTC)** em compilação nativa (**Native Image**) e **Spring Boot 4**, ele adota os princípios de **Arquitetura Hexagonal (Ports and Adapters)**, operando de forma reativa e desacoplada via **Kafka / Redpanda**.

O bot integra APIs públicas de clima e geocodificação (**Open-Meteo** e **BrasilAPI** para CEPs brasileiros), permitindo aos usuários consultar a temperatura atual, sensação térmica, umidade, vento, probabilidade de chuva, índice UV e previsão estendida para 7 dias. Ele oferece navegação dinâmica por botões interativos do Discord (Anterior, Hoje, Próximo) que atualizam a mesma mensagem em tempo real, suporte a localização padrão por usuário (`/clima-config`), e trilha completa de auditoria persistida em **PostgreSQL**.

---

## 🏛️ Blueprint da Arquitetura

O bot opera de forma desacoplada do Discord: o **discord-gateway-api** consome os eventos da API do Discord e os publica em tópicos Kafka. O `discord-weather-bot` consome os comandos `/clima`, `/clima-config`, `.clima` e as interações de botões, consulta as APIs públicas de clima sob proteção de circuit breakers e publica as respostas prontas com embeds e botões de volta ao Gateway.

```mermaid
graph TD
    subgraph Discord Client
        U["Usuários Discord"]
    end

    subgraph "Gateway Layer"
        DGW["discord-gateway-api"]
    end

    subgraph "Kafka Event Broker (Redpanda)"
        TOP_CMD["discord.events.interaction.command"]
        TOP_MSG["discord.events.message.command"]
        TOP_BTN["discord.events.interaction.button"]
        TOP_RESP["discord.gateway.responses"]
        TOP_REG["discord.gateway.commands"]
    end

    subgraph "Core: discord-weather-bot (Java 25 + Oracle GraalVM)"
        subgraph "Inbound Adapters"
            IEC["InboundEventConsumer"]
            IEM["InboundEventMapper"]
        end

        subgraph "Application Core (Hexagonal)"
            APP_SVC["WeatherApplicationService"]
            USE_QRY["ExecuteWeatherQueryUseCase"]
            USE_CFG["ConfigureUserWeatherUseCase"]
            USE_NAV["NavigateWeatherUseCase"]
            LAYOUT["WeatherLayoutBuilder"]
            PARSER["CommandTextParser"]
            W_TRANS["WeatherCodeTranslator"]
        end

        subgraph "Outbound Adapters"
            W_CLIENT["OpenMeteoWeatherClient (RestClient + Resilience4j)"]
            JDBC_CFG["JdbcUserWeatherConfigAdapter (UserWeatherConfigPort)"]
            JDBC_SESS["JdbcWeatherSessionAdapter (WeatherSessionPort)"]
            JDBC_AUDIT["JdbcWeatherAuditLogAdapter (WeatherAuditLogPort)"]
            RESP_PUB["GatewayResponsePublisher (ReplyPublisherPort)"]
            CMD_REG["CommandRegistrar (ApplicationRunner)"]
        end
    end

    subgraph "Serviços Externos e Infraestrutura"
        OPENMETEO["Open-Meteo (Geocoding & Forecast APIs)"]
        BRASILAPI["BrasilAPI (Resolução de CEP v2)"]
        POSTGRES["PostgreSQL (weather_bot schema)"]
        CONSUL["Consul Server (KV Config)"]
        VAULT["HashiCorp Vault (Secrets Injector)"]
    end

    U --> DGW
    DGW --> TOP_CMD
    DGW --> TOP_MSG
    DGW --> TOP_BTN

    TOP_CMD --> IEC
    TOP_MSG --> IEC
    TOP_BTN --> IEC

    IEC --> IEM
    IEM --> USE_QRY
    IEM --> USE_CFG
    IEM --> USE_NAV

    USE_QRY --> APP_SVC
    USE_CFG --> APP_SVC
    USE_NAV --> APP_SVC

    APP_SVC --> LAYOUT
    APP_SVC --> W_CLIENT
    APP_SVC --> JDBC_CFG
    APP_SVC --> JDBC_SESS
    APP_SVC --> JDBC_AUDIT
    APP_SVC --> RESP_PUB

    W_CLIENT --> OPENMETEO
    W_CLIENT --> BRASILAPI
    JDBC_CFG --> POSTGRES
    JDBC_SESS --> POSTGRES
    JDBC_AUDIT --> POSTGRES
    RESP_PUB --> TOP_RESP
    CMD_REG --> TOP_REG

    TOP_RESP --> DGW
    TOP_REG --> DGW
    DGW --> U
```

---

## ⚡ Fluxo de Execução e Paginação Interativa

```mermaid
sequenceDiagram
    autonumber
    actor Usuario as Usuário Discord
    participant Gateway as discord-gateway-api
    participant Kafka as Kafka / Redpanda
    participant Bot as discord-weather-bot
    participant OpenMeteo as Open-Meteo API
    participant BrasilAPI as BrasilAPI
    participant DB as PostgreSQL (weather_bot)

    Usuario->>Gateway: Dispara /clima local: "São Paulo"
    Gateway->>Kafka: Publica discord.events.interaction.command
    Kafka->>Bot: Consome InboundEvent
    Bot->>OpenMeteo: Geocoding search (São Paulo) -> Obtém Lat/Lon
    Bot->>OpenMeteo: Forecast (Lat/Lon) -> Clima Atual + 7 Dias
    Bot->>DB: Salva WeatherSession com previsão dos dias (TTL 1h)
    Bot->>DB: Registra log de auditoria com latência e status
    Bot->>Kafka: Publica GatewayResponse (Embed + Botões [⬅️ Anterior] [📅 Hoje] [Próximo ➡️])
    Kafka->>Gateway: Despacha resposta para o Discord
    Gateway-->>Usuario: Exibe Embed do Dia 1 (Hoje) com Botões

    Usuario->>Gateway: Clica no botão [Próximo ➡️]
    Gateway->>Kafka: Publica discord.events.interaction.button
    Kafka->>Bot: Consome clique de botão (componentId: weather:next:sessionId)
    Bot->>DB: Recupera WeatherSession e avança para o Dia 2
    Bot->>Kafka: Publica UPDATE_MESSAGE com previsão do Dia 2
    Kafka->>Gateway: Edita a mensagem existente no canal
    Gateway-->>Usuario: Mensagem atualizada instantaneamente no mesmo card
```

---

## 📂 Estrutura Hexagonal do Projeto

```
src/
├── main/
│   ├── java/dev/gdinizds/discordweatherbot/
│   │   ├── DiscordWeatherBotApplication.java
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── Location.java                 # Value Object de coordenadas e localização
│   │   │   │   ├── CurrentWeather.java           # Temperatura atual, sensação, vento e umidade
│   │   │   │   ├── DailyForecast.java            # Previsão diária com índices de chuva e UV
│   │   │   │   ├── WeatherForecast.java          # Agregado da previsão atual + diária
│   │   │   │   ├── WeatherSession.java           # Sessão de navegação interativa entre dias
│   │   │   │   ├── UserWeatherConfig.java        # Localização padrão configurada pelo usuário
│   │   │   │   ├── WeatherAuditLog.java          # Registro imutável de auditoria
│   │   │   │   ├── WeatherQueryCommand.java      # Comando de consulta de clima
│   │   │   │   ├── ConfigureWeatherCommand.java  # Comando de configuração de favorito
│   │   │   │   ├── WeatherNavigationCommand.java # Comando de navegação por botões
│   │   │   │   └── OutboundResponse.java         # Resposta para o Discord com Embeds e Botões
│   │   │   └── service/
│   │   │       ├── WeatherCodeTranslator.java    # Tradutor de códigos WMO para emojis e descrições
│   │   │       ├── CommandTextParser.java        # Parser flexível para comandos .clima
│   │   │       └── WeatherLayoutBuilder.java     # Renderizador de Embeds e ActionRows
│   │   ├── application/
│   │   │   ├── port/
│   │   │   │   ├── in/
│   │   │   │   │   ├── ExecuteWeatherQueryUseCase.java
│   │   │   │   │   ├── ConfigureUserWeatherUseCase.java
│   │   │   │   │   └── NavigateWeatherUseCase.java
│   │   │   │   └── out/
│   │   │   │       ├── WeatherClientPort.java
│   │   │   │       ├── UserWeatherConfigPort.java
│   │   │   │       ├── WeatherSessionPort.java
│   │   │   │       ├── WeatherAuditLogPort.java
│   │   │   │       └── ReplyPublisherPort.java
│   │   │   └── service/
│   │   │       ├── WeatherApplicationService.java
│   │   │       └── Mdc.java
│   │   ├── adapter/
│   │   │   ├── in/kafka/
│   │   │   │   ├── InboundEvent.java
│   │   │   │   ├── InboundEventMapper.java
│   │   │   │   └── InboundEventConsumer.java
│   │   │   └── out/
│   │   │       ├── openmeteo/OpenMeteoWeatherClient.java
│   │   │       ├── persistence/
│   │   │       │   ├── JdbcUserWeatherConfigAdapter.java
│   │   │       │   ├── JdbcWeatherSessionAdapter.java
│   │   │       │   └── JdbcWeatherAuditLogAdapter.java
│   │   │       └── kafka/
│   │   │           ├── GatewayResponse.java
│   │   │           ├── GatewayResponsePublisher.java
│   │   │           └── CommandRegistrar.java
│   │   └── config/
│   │       ├── ApplicationConfig.java
│   │       ├── WeatherBotProperties.java
│   │       ├── KafkaConfig.java
│   │       ├── ResilienceConfig.java
│   │       ├── Resilience.java
│   │       └── NativeRuntimeHints.java
│   └── resources/
│       ├── application.yaml
│       └── db/migration/
│           └── V1__init_weather_schema.sql
└── test/
    └── java/dev/gdinizds/discordweatherbot/
        ├── arch/ArchitectureTest.java            # ArchUnit validando arquitetura hexagonal
        ├── domain/
        ├── application/
        └── adapter/
```

---

## 🎯 Comandos Suportados

### 1. Slash Command: `/clima`
Registrado automaticamente no Discord Gateway via `discord.gateway.commands`.

| Parâmetro | Tipo | Obrigatório | Descrição | Exemplo |
| :--- | :--- | :---: | :--- | :--- |
| `local` | `STRING` | Não | Cidade ou CEP/Zipcode (opcional se você já configurou seu padrão) | `São Paulo` ou `01001-000` |
| `estado` | `STRING` | Não | Estado ou UF | `SP` |
| `pais` | `STRING` | Não | País | `Brasil` |

> Caso executado sem parâmetros, o bot consulta automaticamente a localização padrão configurada para o usuário. Se ainda não houver configuração, exibe instruções amigáveis para definição.

### 2. Slash Command: `/clima-config`
Configura a localização padrão exclusiva do usuário. A resposta é enviada de forma efêmera (`EPHEMERAL_REPLY`), visível somente para ele.

| Parâmetro | Tipo | Obrigatório | Descrição | Exemplo |
| :--- | :--- | :---: | :--- | :--- |
| `cidade` | `STRING` | **Sim** | Sua cidade ou CEP padrão | `Curitiba` |
| `estado` | `STRING` | Não | Estado ou UF padrão | `PR` |
| `pais` | `STRING` | Não | País padrão | `Brasil` |

### 3. Dot Command: `.clima` e `.clima-config`
Processa mensagens de texto comuns com prefixo `.`:

```bash
# Consulta usando a localização padrão salva
.clima

# Consulta informando cidade ou CEP
.clima Curitiba
.clima 01001-000
.clima Rio de Janeiro, RJ, Brasil

# Configuração de localização padrão do usuário
.clima config Florianópolis, SC, Brasil
.clima configure Belo Horizonte
.clima-config Salvador, BA, Brasil
```

---

## 🌤️ Layout e Navegação Interativa

A resposta do bot é estruturada com Embeds ricos e dinâmicos:

- **Embed Principal:**
  - **Título:** Emoji temático da condição + Nome completo da localidade (`☀️ Clima em São Paulo, SP - Brasil`).
  - **Cor do Embed:** Dinâmica baseada na condição meteorológica (amarelo para ensolarado, azul para chuva, roxo para tempestades, etc.).
  - **Destaque:** Temperatura atual em tamanho grande, sensação térmica e descrição da condição WMO.
  - **Campos Detalhados:**
    - `🌡️ Máx / Mín`: Temperaturas extremas do dia.
    - `💧 Umidade`: Umidade relativa do ar em percentual.
    - `💨 Vento`: Velocidade do vento em km/h.
    - `🌧️ Chuva`: Probabilidade (%) e volume acumulado (mm).
    - `☀️ Índice UV`: Escala de radiação ultravioleta.
    - `📅 Data`: Dia da semana e data completa formatada em português.
  - **Visão Semanal:** Resumo dos próximos dias com mínimas, máximas e probabilidades de chuva.
- **Botões Interativos (ActionRow):**
  - `⬅️ Anterior`: retrocede um dia na previsão (desabilitado no dia de hoje).
  - `📅 Hoje`: retorna instantaneamente para a previsão do dia atual.
  - `Próximo ➡️`: avança para o próximo dia da previsão (desabilitado no 7º dia).

---

## 📊 Persistência e Tabela de Auditoria (PostgreSQL)

O schema `weather_bot` é versionado via **Flyway** e armazena:

```sql
CREATE TABLE weather_bot.user_weather_config (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(32) NOT NULL UNIQUE,
    city VARCHAR(128) NOT NULL,
    state VARCHAR(64),
    country VARCHAR(64),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE weather_bot.weather_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    guild_id VARCHAR(32),
    channel_id VARCHAR(32),
    user_id VARCHAR(32) NOT NULL,
    origin_message_id VARCHAR(32),
    location_name VARCHAR(256) NOT NULL,
    location_state VARCHAR(64),
    location_country VARCHAR(64),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    current_day_index INT NOT NULL DEFAULT 0,
    forecast_json TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE weather_bot.weather_audit_log (
    id BIGSERIAL PRIMARY KEY,
    correlation_id VARCHAR(64) NOT NULL,
    guild_id VARCHAR(32),
    channel_id VARCHAR(32),
    user_id VARCHAR(32) NOT NULL,
    command_name VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(16) NOT NULL,
    query_location VARCHAR(256),
    resolved_location VARCHAR(256),
    status VARCHAR(32) NOT NULL,
    raw_request TEXT,
    raw_response TEXT,
    execution_time_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 🛡️ Resiliência com Resilience4j

Todas as chamadas para serviços externos contam com proteção de falha em cascata:

| Alvo | Circuit Breaker | Retry | Time Limiter | Bulkhead |
| :--- | :---: | :---: | :---: | :---: |
| **Open-Meteo Weather API** | 50% falha, janela de 10 | 2 tentativas com backoff | 6s | - |
| **BrasilAPI (CEP)** | 50% falha, janela de 10 | 2 tentativas | 4s | - |
| **PostgreSQL Database** | 50% falha, janela de 20 | 3 tentativas | - | - |
| **Processamento Concorrente** | - | - | - | 32 chamadas simultâneas |

---

## 🚀 Compilação Nativa com Oracle GraalVM (GFTC)

O binário nativo é gerado pelo plugin `org.graalvm.buildtools.native` utilizando a imagem oficial da Oracle:

```dockerfile
FROM container-registry.oracle.com/graalvm/native-image:25 AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew nativeCompile --no-daemon -x test

FROM debian:bookworm-slim AS runtime
WORKDIR /app
COPY --from=build /workspace/build/native/nativeCompile/discord-weather-bot /app/discord-weather-bot
USER 1001
ENTRYPOINT ["/app/discord-weather-bot", "-XX:MaximumHeapSizePercent=50"]
```

Benefícios da execução nativa:
- **Tempo de Inicialização:** < 80ms para ficar pronto e responder ao Kafka.
- **Consumo de Memória Base:** ~45MB RSS (em comparação aos ~350MB de uma JVM tradicional).
- **Segurança:** Superfície de ataque reduzida com contêiner `debian:bookworm-slim` sem ferramentas de desenvolvimento e sem execução como root.

---

## 🚢 Deploy no Kubernetes via GitOps (FluxCD)

O ciclo de vida no cluster é gerenciado através do repositório GitOps [fleet-infra](https://github.com/gdinizds/fleet-infra.git):

- **Injeção de Segredos pelo HashiCorp Vault:**
  - Banco de Dados: `secret/discord-weather-bot/db`
- **Configurações Centralizadas no Consul:**
  - Carregadas automaticamente via Spring Cloud Consul Config em `consul-server.consul.svc.cluster.local:8500`.
- **Image Update Automation do Flux:**
  - A cada nova tag gerada no GitHub (`v*.*.*`), o GitHub Actions constrói a imagem no GHCR e o Flux aplica a atualização no cluster via RollingUpdate.

---

## 🛠️ Comandos de Desenvolvimento

```bash
# Executar todos os testes automatizados
./gradlew test

# Executar aplicação localmente
./gradlew bootRun

# Compilar binário nativo localmente
./gradlew nativeCompile
```

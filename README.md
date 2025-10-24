# CDB Registry - Dead Letter Exchange & Retry Mechanisms

Este projeto demonstra a implementação de **Dead Letter Exchange (DLX)** e **Dead Letter Queue (DLQ)** com RabbitMQ, incluindo mecanismos de retry para tratamento de falhas em processos de renda fixa (CDB).

## Arquitetura Multi-Módulo Maven

O projeto utiliza uma estrutura **multi-módulo Maven** (também conhecida como Maven Multi-Module ou Maven Reactor). Esta é uma abordagem profissional para organizar projetos com múltiplas aplicações ou componentes relacionados.

### Por que Multi-Módulo?

#### 1. **Organização e Separação de Responsabilidades**
Ao invés de ter tudo em um único projeto monolítico, separamos:
- **Producer**: Responsável apenas por receber requisições HTTP e publicar mensagens
- **Consumer**: Responsável apenas por consumir e processar mensagens (com tratamento de erros)

Cada módulo tem seu próprio ciclo de vida, dependências e configurações específicas.

#### 2. **Reutilização de Configurações**
O **POM Pai** (`pom.xml` na raiz) centraliza:
- Versões de dependências (`dependencyManagement`)
- Configurações de plugins (`pluginManagement`)
- Propriedades do projeto (Java version, encoding, etc.)

Isso evita duplicação e garante consistência entre os módulos.

#### 3. **Build Unificado**
Com um único comando `mvn clean install` na raiz, o Maven:
1. Identifica todos os módulos declarados no POM pai
2. Determina a ordem de build (Reactor Order)
3. Compila todos os módulos na sequência correta

#### 4. **Facilita Deploy Independente**
- Cada módulo gera seu próprio JAR executável
- Você pode fazer deploy apenas do módulo que mudou
- Cada módulo pode ter seu próprio Dockerfile otimizado

#### 5. **Isolamento de Dependências**
- Producer precisa de `spring-boot-starter-web` (para REST)
- Consumer **não precisa** de Web (só messaging)
- Cada um declara apenas o que realmente usa

### Estrutura do Projeto

```
registry-cdb-dlx-retry-mq/               ← Projeto Pai
├── pom.xml                              ← POM Pai (packaging: pom)
│   ├── <modules>
│   │   ├── producer                     ← Declaração dos módulos
│   │   └── consumer
│   ├── <dependencyManagement>           ← Versões centralizadas
│   └── <pluginManagement>               ← Configurações de plugins
│
├── producer/                            ← Módulo 1
│   ├── pom.xml                          ← POM do módulo (parent: pom pai)
│   ├── src/
│   ├── Dockerfile
│   └── target/                          ← JAR independente gerado
│       └── producer-0.0.1-SNAPSHOT.jar
│
└── consumer/                            ← Módulo 2
    ├── pom.xml                          ← POM do módulo (parent: pom pai)
    ├── src/
    ├── Dockerfile
    └── target/                          ← JAR independente gerado
        └── consumer-0.0.1-SNAPSHOT.jar
```

### Como Funciona o POM Pai

#### POM Pai (`pom.xml` na raiz)
```xml
<packaging>pom</packaging>  <!-- NÃO gera JAR, apenas coordena -->

<modules>
    <module>producer</module>  <!-- Lista de módulos -->
    <module>consumer</module>
</modules>

<dependencyManagement>  <!-- Define versões, mas NÃO adiciona dependências -->
    <dependencies>
        <dependency>
            <groupId>org.openapitools</groupId>
            <artifactId>jackson-databind-nullable</artifactId>
            <version>0.2.1</version>  <!-- Versão centralizada -->
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### POM dos Módulos
```xml
<parent>  <!-- Herda configurações do pai -->
    <groupId>br.com.iagoomes</groupId>
    <artifactId>registry-cdb-dlx-retry-mq</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>

<artifactId>producer</artifactId>  <!-- Apenas artifactId (groupId e version herdados) -->

<dependencies>
    <dependency>
        <groupId>org.openapitools</groupId>
        <artifactId>jackson-databind-nullable</artifactId>
        <!-- SEM versão! Usa a do pai -->
    </dependency>
</dependencies>
```

### Vantagens para Este Projeto

1. **Simula Arquitetura Real**: Em produção, producer e consumer geralmente são serviços separados
2. **Deploy Independente**: Cada serviço pode escalar independentemente no Docker/Kubernetes
3. **Manutenção Facilitada**: Mudanças no Producer não afetam o Consumer
4. **Consistência**: Ambos usam as mesmas versões de Spring Boot e RabbitMQ configuradas no pai

## Conceitos: Dead Letter Exchange (DLX) e Dead Letter Queue (DLQ)

### O que é DLX?

**Dead Letter Exchange (DLX)** é um exchange especial do RabbitMQ que recebe mensagens que não puderam ser processadas com sucesso. Mensagens podem ser "mortas" por diversos motivos:

- **Rejeição**: Consumidor rejeita a mensagem (`basicReject` ou `basicNack`)
- **TTL Expirado**: Mensagem fica na fila por tempo superior ao configurado
- **Fila Cheia**: Fila atinge limite máximo de mensagens/tamanho

### O que é DLQ?

**Dead Letter Queue (DLQ)** é a fila destino onde as mensagens "mortas" são armazenadas. Ela funciona como um repositório de mensagens problemáticas que podem ser:
- Analisadas para diagnóstico
- Reprocessadas manualmente
- Encaminhadas para um fluxo de retry

### Fluxo Completo com DLX/DLQ

```
┌────────────────────────────────────────────────────────────────┐
│                    Fluxo Normal (Sucesso)                      │
└────────────────────────────────────────────────────────────────┘

Producer → Exchange → Queue → Consumer → ✓ Processado


┌────────────────────────────────────────────────────────────────┐
│                  Fluxo de Erro (com DLX/DLQ)                   │
└────────────────────────────────────────────────────────────────┘

Producer → Exchange → Queue → Consumer → ✗ ERRO!
                        │
                        └─→ DLX (fixed-income.dlx)
                             └─→ DLQ (fixed-income.dlq)
                                  ├─→ Análise manual
                                  ├─→ Retry automático
                                  └─→ Alertas/Monitoramento
```

### Configuração da Fila Principal com DLX

No arquivo `RabbitMQConfig.java`:

```java
@Bean
public Queue fixedIncomeQueue() {
    return QueueBuilder.durable(this.queue)
            .deadLetterExchange(this.exchangeDeadLetter)      // ← DLX configurado
            .deadLetterRoutingKey(this.routingKeyDeadLetter)  // ← Routing key para DLQ
            .build();
}
```

**O que isso significa?**
- Quando uma mensagem é rejeitada ou expira na fila `fixed-income.cdb.registry`
- Ela é automaticamente enviada para o exchange `fixed-income.dlx`
- Com a routing key `cdb.registry.error`
- Que roteia para a fila `fixed-income.dlq`

### Producer (Porta 8080)

- Expõe endpoint REST para criar registros de CDB
- Envia mensagens para o RabbitMQ na fila principal
- **Não tem conhecimento de DLX/DLQ** (responsabilidade do consumer)
- Endpoint: `POST /api/v1/cdb-registry`

### Consumer (Porta 8081)

- Consome mensagens do RabbitMQ
- Processa registros de CDB recebidos
- **Em caso de erro**: Rejeita a mensagem (enviando para DLQ)
- **Modo ACK**: `AUTO` (pode ser configurado como `MANUAL` para controle fino)
- Logs detalhados de processamento

## Fluxo de Mensageria

### Cenário 1: Processamento com Sucesso
```
Producer API → Exchange (fixed-income.direct)
            → Routing Key (cdb.registry.created)
            → Queue (fixed-income.cdb.registry)
            → Consumer API
            → ✓ ACK (mensagem confirmada e removida da fila)
```

### Cenário 2: Processamento com Erro
```
Producer API → Exchange (fixed-income.direct)
            → Routing Key (cdb.registry.created)
            → Queue (fixed-income.cdb.registry)
            → Consumer API
            → ✗ NACK/REJECT (erro no processamento)
            → DLX (fixed-income.dlx)
            → DLQ (fixed-income.dlq)
            → Mensagem armazenada para análise/retry
```

## Pré-requisitos

- Docker e Docker Compose
- Java 21 (para desenvolvimento local)
- Maven 3.9+ (para desenvolvimento local)

## Como Executar

### Com Docker Compose (Recomendado)

```bash
# Build e start todos os serviços
docker-compose up --build

# Ou em background
docker-compose up -d --build

# Ver logs
docker-compose logs -f

# Ver logs de um serviço específico
docker-compose logs -f producer-api
docker-compose logs -f consumer-api

# Parar os serviços
docker-compose down
```

### Desenvolvimento Local

```bash
# Build do projeto
mvn clean install

# Terminal 1 - RabbitMQ
docker-compose up rabbitmq

# Terminal 2 - Producer
cd producer
mvn spring-boot:run

# Terminal 3 - Consumer
cd consumer
mvn spring-boot:run
```

## Testando o Fluxo Normal

### Criar um registro de CDB

```bash
curl -X POST http://localhost:8080/api/v1/cdb-registry \
  -H "Content-Type: application/json" \
  -d '{
    "registryId": "CDB-001",
    "clientId": "CLI-12345",
    "amount": 10000.00,
    "durationDays": 365,
    "interestRate": 13.65,
    "createdAt": "2025-10-24T10:30:00"
  }'
```

**Resposta esperada:**
```json
{
  "registryId": "CDB-001",
  "clientId": "CLI-12345",
  "amount": 10000.00,
  "durationDays": 365,
  "interestRate": 13.65,
  "createdAt": "2025-10-24T10:30:00"
}
```

### Verificar Consumer

Verifique os logs do consumer para ver a mensagem sendo processada:

```bash
docker-compose logs -f consumer-api
```

Você deve ver logs como:
```
consumer-api  | Processing CDB registry: CdbRegistryDto(registryId=CDB-001, clientId=CLI-12345, ...)
consumer-api  | Registry ID: CDB-001, Client: CLI-12345, Amount: 10000.00, Duration: 365 days, Interest Rate: 13.65%
```

## Testando DLX/DLQ (Simulando Erro)

### 1. Modificar o Consumer para Simular Erro

Edite `CdbRegistryConsume.java` e adicione uma condição para forçar erro:

```java
@RabbitListener(queues = "${fixed-income.queue.name}")
public void handleCdbRegistryCreated(CdbRegistryDto cdbRegistry) {
    log.info("Processing CDB registry: {}", cdbRegistry);

    // Simula erro para demonstrar DLX/DLQ
    if (cdbRegistry.getAmount().compareTo(new BigDecimal("5000")) < 0) {
        log.error("Amount too low! Rejecting message: {}", cdbRegistry);
        throw new RuntimeException("Minimum amount is 5000");
    }

    log.info("Registry ID: {}, Client: {}, Amount: {}, Duration: {} days, Interest Rate: {}%",
            cdbRegistry.getRegistryId(),
            cdbRegistry.getClientId(),
            cdbRegistry.getAmount(),
            cdbRegistry.getDurationDays(),
            cdbRegistry.getInterestRate());
}
```

### 2. Enviar Mensagem que Causará Erro

```bash
curl -X POST http://localhost:8080/api/v1/cdb-registry \
  -H "Content-Type: application/json" \
  -d '{
    "registryId": "CDB-ERROR-001",
    "clientId": "CLI-99999",
    "amount": 1000.00,
    "durationDays": 30,
    "interestRate": 10.0,
    "createdAt": "2025-10-24T10:30:00"
  }'
```

### 3. Verificar DLQ no RabbitMQ Management

1. Acesse: http://localhost:15672 (guest/guest)
2. Vá em **Queues** → `fixed-income.dlq`
3. Você verá a mensagem com erro armazenada
4. Clique em **Get messages** para visualizar o conteúdo

### 4. Logs Esperados

**Consumer:**
```
consumer-api  | Processing CDB registry: CdbRegistryDto(registryId=CDB-ERROR-001, amount=1000.00, ...)
consumer-api  | ERROR - Amount too low! Rejecting message: ...
consumer-api  | Exception: Minimum amount is 5000
```

**RabbitMQ:**
- Mensagem será roteada automaticamente para `fixed-income.dlq`
- Headers adicionais serão incluídos:
  - `x-death`: Histórico de "mortes" da mensagem
  - `x-first-death-reason`: Razão da primeira falha
  - `x-first-death-queue`: Fila original

## Acessos

- **Producer API**: http://localhost:8080
- **Consumer API**: http://localhost:8081
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)

## Estrutura dos Módulos

### Producer
```
producer/src/main/java/
└── br/com/iagoomes/
    ├── ProducerApplication.java
    ├── application/
    │   ├── controller/
    │   │   └── CdbRegistryController.java
    │   └── service/
    │       └── CdbRegistryService.java
    ├── domain/
    │   └── dto/
    │       └── CdbRegistryDto.java
    └── infra/
        ├── config/
        │   └── RabbitMQConfig.java        ← Configuração DLX/DLQ
        └── mqprovider/producer/
            └── CdbRegistryProducer.java
```

### Consumer
```
consumer/src/main/java/
└── br/com/iagoomes/consumer/
    ├── ConsumerApplication.java
    ├── domain/
    │   └── dto/
    │       └── CdbRegistryDto.java
    └── infra/
        ├── config/
        │   └── RabbitMQConfig.java        ← Configuração DLX/DLQ
        └── mqprovider/consume/
            └── CdbRegistryConsume.java    ← Listener com tratamento de erros
```

## Configurações RabbitMQ

### Filas e Exchanges

| Componente | Nome | Descrição |
|-----------|------|-----------|
| **Queue Principal** | `fixed-income.cdb.registry` | Fila principal (com DLX configurado) |
| **Exchange Principal** | `fixed-income.direct` | Exchange do fluxo normal |
| **Routing Key** | `cdb.registry.created` | Chave de roteamento normal |
| **Dead Letter Exchange** | `fixed-income.dlx` | Exchange para mensagens com erro |
| **Dead Letter Queue** | `fixed-income.dlq` | Fila de mensagens mortas |
| **DL Routing Key** | `cdb.registry.error` | Chave de roteamento para DLQ |

### Variáveis de Ambiente (application.yml)

```yaml
fixed-income:
  queue:
    name: fixed-income.cdb.registry
    exchange: fixed-income.direct
    routing-key: cdb.registry.created
    dead-letter-exchange: fixed-income.dlx
    dead-letter-queue: fixed-income.dlq
    dead-letter-routing-key: cdb.registry.error
```

## Estratégias de Retry

### Opção 1: Retry Manual (Atual)
- Mensagens ficam na DLQ
- Administrador analisa e decide:
  - Reprocessar manualmente
  - Corrigir dados e reinserir
  - Descartar se inválidas

### Opção 2: Retry Automático com TTL (Futuro)
```java
@Bean
public Queue retryQueue() {
    return QueueBuilder.durable("fixed-income.retry")
            .ttl(60000)  // 60 segundos
            .deadLetterExchange(this.exchange)
            .deadLetterRoutingKey(this.routingKey)
            .build();
}
```

**Fluxo:**
1. Mensagem falha → vai para retry queue
2. Aguarda 60 segundos (TTL)
3. Retorna para fila principal automaticamente
4. Tenta processar novamente

### Opção 3: Retry com Backoff Exponencial
- Primeira tentativa: aguarda 30s
- Segunda tentativa: aguarda 60s
- Terceira tentativa: aguarda 120s
- Após 3 tentativas: DLQ final

## Monitoramento

### Métricas Importantes

1. **Taxa de Erro**: Mensagens na DLQ vs. processadas
2. **Tempo na Fila**: Latência de processamento
3. **Tamanho da DLQ**: Acúmulo de mensagens problemáticas

### Alertas Sugeridos

- DLQ com mais de 100 mensagens
- Taxa de erro > 5%
- Consumer parado (sem heartbeat)

## Tecnologias

- **Spring Boot 3.5.7**
- **Java 21**
- **RabbitMQ 3 Management**
- **Maven Multi-Module**
- **Docker & Docker Compose**
- **Lombok**
- **Jackson** (com suporte a JSR-310 para datas)

## Próximos Passos

- [ ] Implementar retry automático com backoff exponencial
- [ ] Adicionar métricas com Micrometer
- [ ] Criar API para gerenciar DLQ (visualizar, reprocessar, descartar)
- [ ] Implementar circuit breaker
- [ ] Adicionar tracing distribuído
- [ ] Testes de integração com Testcontainers

## Referências

- [RabbitMQ - Dead Letter Exchanges](https://www.rabbitmq.com/docs/dlx)
- [Spring AMQP Documentation](https://docs.spring.io/spring-amqp/reference/)
- [Maven Multi-Module Projects](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
# Registry CDB DLX Retry MQ

Este projeto demonstra a implementação de **Dead Letter Exchange (DLX)** e **Dead Letter Queue (DLQ)** com mecanismos de retry para tratamento de falhas em processos de renda fixa (CDB).

## 📋 Sobre o Projeto

Este é o **Exercício 2** da série de aprendizado de RabbitMQ, focado em tratamento de erros e recuperação de falhas.

**O que você aprenderá:**
- ✅ Dead Letter Exchange (DLX) e Dead Letter Queue (DLQ)
- ✅ Rejeição e reprocessamento de mensagens
- ✅ Retry automático com TTL
- ✅ Headers especiais de morte (`x-death`)
- ✅ Tratamento robusto de falhas
- ✅ Análise e diagnóstico de mensagens problemáticas

## 🏗️ Arquitetura

```
Fluxo Normal (Sucesso)
Producer → Exchange → Queue → Consumer → ✓ Processado

Fluxo de Erro (com DLX/DLQ)
Producer → Exchange → Queue → Consumer → ✗ ERRO!
                                            ↓
                        DLX (fixed-income.dlx)
                                ↓
                        DLQ (fixed-income.dlq)
                                ├─→ Análise manual
                                ├─→ Retry automático
                                └─→ Alertas/Monitoramento
```

## 📦 Estrutura do Projeto

O projeto utiliza **Maven Multi-Module**, separando Producer e Consumer em módulos independentes:

```
registry-cdb-dlx-retry-mq/
├── pom.xml (POM Pai)
├── producer/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/
└── consumer/
    ├── pom.xml
    ├── Dockerfile
    └── src/main/java/
```

**Vantagens:**
- Deploy independente de Producer e Consumer
- Escalabilidade horizontal isolada
- Cada módulo com suas próprias dependências
- Simula arquitetura de microserviços

## 🛠️ Tecnologias

- Java 21
- Spring Boot 3.5.7
- Spring AMQP
- RabbitMQ 3
- Docker & Docker Compose
- Maven Multi-Module
- Lombok
- Jackson (com suporte a JSR-310 para datas)

## ✅ Pré-requisitos

- Docker e Docker Compose instalados
- Java 21 (para desenvolvimento local)
- Maven 3.9+ (para desenvolvimento local)
- Portas 8080, 8081, 5672 e 15672 disponíveis

## 🚀 Como Executar

### Com Docker Compose (recomendado)

```bash
# Clonar o repositório
git clone https://github.com/iagoomes/registry-cdb-dlx-retry-mq.git
cd registry-cdb-dlx-retry-mq

# Buildar e iniciar todos os serviços
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

### Em Desenvolvimento Local

```bash
# Terminal 1 - RabbitMQ
docker-compose up rabbitmq

# Terminal 2 - Build do projeto
mvn clean install

# Terminal 3 - Producer
cd producer
mvn spring-boot:run

# Terminal 4 - Consumer
cd consumer
mvn spring-boot:run
```

## 📁 Estrutura de Código

### Producer

```
producer/src/main/java/br/com/iagoomes/
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
    │   └── RabbitMQConfig.java
    └── mqprovider/producer/
        └── CdbRegistryProducer.java
```

### Consumer

```
consumer/src/main/java/br/com/iagoomes/consumer/
├── ConsumerApplication.java
├── domain/
│   └── dto/
│       └── CdbRegistryDto.java
└── infra/
    ├── config/
    │   └── RabbitMQConfig.java
    └── mqprovider/consume/
        └── CdbRegistryConsume.java
```

## 📡 Endpoints da API

### Producer API (http://localhost:8080)

**Criar Registro de CDB**
```bash
POST /api/v1/cdb-registry
Content-Type: application/json

{
  "registryId": "CDB-001",
  "clientId": "CLI-12345",
  "amount": 10000.00,
  "durationDays": 365,
  "interestRate": 13.65,
  "createdAt": "2025-10-24T10:30:00"
}
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

## 📚 Dead Letter Exchange (DLX) e Dead Letter Queue (DLQ)

### O que é DLX?

**Dead Letter Exchange (DLX)** é um exchange especial do RabbitMQ que recebe mensagens que não puderam ser processadas com sucesso.

Mensagens podem ser "mortas" por diversos motivos:
- **Rejeição:** Consumidor rejeita a mensagem (`basicReject` ou `basicNack`)
- **TTL Expirado:** Mensagem fica na fila por tempo superior ao configurado
- **Fila Cheia:** Fila atinge limite máximo de mensagens/tamanho

### O que é DLQ?

**Dead Letter Queue (DLQ)** é a fila destino onde as mensagens "mortas" são armazenadas. Ela funciona como um repositório de mensagens problemáticas que podem ser:
- Analisadas para diagnóstico
- Reprocessadas manualmente
- Encaminhadas para um fluxo de retry

### Configuração de DLX

No arquivo `RabbitMQConfig.java`:

```java
@Bean
public Queue fixedIncomeQueue() {
    return QueueBuilder.durable(this.queue)
        .deadLetterExchange(this.exchangeDeadLetter) // ← DLX configurado
        .deadLetterRoutingKey(this.routingKeyDeadLetter) // ← Routing key para DLQ
        .build();
}
```

**O que isso significa?**
- Quando uma mensagem é rejeitada ou expira na fila `fixed-income.cdb.registry`
- Ela é automaticamente enviada para o exchange `fixed-income.dlx`
- Com a routing key `cdb.registry.error`
- Que roteia para a fila `fixed-income.dlq`

### Fluxo de Processamento

| Situação | Fluxo |
|----------|-------|
| **Sucesso** | Producer → Exchange → Queue → Consumer → ✅ ACK → Removida da fila |
| **Erro** | Producer → Exchange → Queue → Consumer → ❌ NACK → DLX → DLQ → Análise |

## 🔄 Fluxo de Processamento

### Producer

- Expõe endpoint REST para criar registros de CDB
- Envia mensagens para o RabbitMQ na fila principal
- Não tem conhecimento de DLX/DLQ (responsabilidade do consumer)

**Endpoint:** `POST /api/v1/cdb-registry`

### Consumer

- Consome mensagens do RabbitMQ
- Processa registros de CDB recebidos
- Em caso de erro: Rejeita a mensagem (enviando para DLQ)
- Modo ACK: `AUTO` (pode ser configurado como `MANUAL` para controle fino)
- Logs detalhados de processamento

## 💡 Exemplos de Uso

### Exemplo 1: Requisição com Sucesso

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

**Logs esperados do consumer:**

```
consumer-api | Processing CDB registry: CdbRegistryDto(registryId=CDB-001, clientId=CLI-12345, ...)
consumer-api | Registry ID: CDB-001, Client: CLI-12345, Amount: 10000.00, Duration: 365 days, Interest Rate: 13.65%
```

### Exemplo 2: Simular Erro para Demonstrar DLX/DLQ

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

Agora faça uma requisição com valor menor que 5000:

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

**Logs esperados:**

```
consumer-api | Processing CDB registry: CdbRegistryDto(registryId=CDB-ERROR-001, amount=1000.00, ...)
consumer-api | ERROR - Amount too low! Rejecting message: ...
consumer-api | Exception: Minimum amount is 5000
```

**RabbitMQ:**
- Mensagem será roteada automaticamente para `fixed-income.dlq`
- Headers adicionais serão incluídos:
  - `x-death`: Histórico de "mortes" da mensagem
  - `x-first-death-reason`: Razão da primeira falha
  - `x-first-death-queue`: Fila original

## 📊 Monitoramento

### RabbitMQ Management UI

Acesse: [http://localhost:15672](http://localhost:15672)

**Credenciais:**
- Username: `guest`
- Password: `guest`

**O que você pode ver:**

1. **Queues**
   - Vá em Queues → `fixed-income.dlq`
   - Você verá a mensagem com erro armazenada
   - Clique em "Get Messages" para visualizar o conteúdo
   - Veja os headers especiais (`x-death`, etc)

2. **Dead Letter Queue**
   - Veja todas as mensagens problemáticas
   - Analise headers para diagnosticar problemas
   - Monitore o crescimento da DLQ

3. **Connections**
   - Veja as conexões ativas do Producer e Consumer

### Ver logs do Consumer

```bash
docker-compose logs -f consumer-api
```

## ⚙️ Configuração

### application.yml

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

fixed-income:
  queue:
    name: fixed-income.cdb.registry
    exchange: fixed-income.direct
    routing-key: cdb.registry.created
    dead-letter-exchange: fixed-income.dlx
    dead-letter-queue: fixed-income.dlq
    dead-letter-routing-key: cdb.registry.error
```

### Variáveis de Ambiente

Você pode sobrescrever as configurações via variáveis de ambiente:

```bash
SPRING_RABBITMQ_HOST=rabbitmq
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=guest
SPRING_RABBITMQ_PASSWORD=guest
```

## 🔧 Troubleshooting

### Verificar se as portas estão em uso

```bash
lsof -i :8080
lsof -i :8081
lsof -i :5672
lsof -i :15672
```

### Parar containers antigos

```bash
docker-compose down -v
```

### Verificar logs do Producer

```bash
docker-compose logs -f producer-api
```

Você deve ver logs de operação normais.

### Verificar conexão com RabbitMQ

- Acesse [http://localhost:15672](http://localhost:15672)
- Vá em Connections
- Deve haver uma conexão do Producer

### Consumer não está recebendo mensagens

**Verificar:**
- Consumer está rodando?
  ```bash
  docker-compose ps
  ```
- Queues foram criadas?
  - Acesse [http://localhost:15672](http://localhost:15672) → Queues
  - Deve existir `fixed-income.cdb.registry` e `fixed-income.dlq`
- Bindings estão corretos?
  - Acesse Exchange `fixed-income.direct`
  - Veja se os bindings estão configurados

**Possíveis causas:**
- Consumer travado (verificar logs)
- Erro na desserialização JSON
- Exception no handler do consumer

**Solução:**
```bash
# Reiniciar o consumer
docker-compose restart consumer-api

# Ver logs detalhados
docker-compose logs -f consumer-api
```

### Mensagens não aparecem na DLQ

**Verificar:**
- DLX está configurado corretamente na queue?
- Consumer está rejeitando as mensagens?
- Os nomes de exchange/queue estão corretos?

**Solução:**
```bash
# Recriar containers
docker-compose down -v
docker-compose up --build
```

## 📈 Estratégias Avançadas

### Retry Automático com Backoff

Implementar retry automático com aumento progressivo de tempo:

```java
@Bean
public Queue retryQueue() {
    return QueueBuilder.durable("fixed-income.retry")
        .ttl(60000) // 60 segundos
        .deadLetterExchange(this.exchange)
        .deadLetterRoutingKey(this.routingKey)
        .build();
}
```

**Fluxo:**
- Mensagem falha → vai para retry queue
- Aguarda 60 segundos (TTL)
- Retorna para fila principal automaticamente
- Tenta processar novamente

### Backoff Exponencial

- Primeira tentativa: aguarda 30s
- Segunda tentativa: aguarda 60s
- Terceira tentativa: aguarda 120s
- Após 3 tentativas: DLQ final

## 📚 Série de Exercícios

- **Exercício 1:** [registry-cdb-basic-concepts-mq](https://github.com/iagoomes/registry-cdb-basic-concepts-mq) - Fluxo básico
- **Exercício 2:** [registry-cdb-dlx-retry-mq](https://github.com/iagoomes/registry-cdb-dlx-retry-mq) - DLX e Retry ← VOCÊ ESTÁ AQUI
- **Exercício 3:** [fixed-income-topic-routing-mq](https://github.com/iagoomes/fixed-income-topic-routing-mq) - Topic Exchange

**Próximos:**
- Exercício 4: Fanout Exchange (Broadcasting)
- Exercício 5: Priority Queues
- Exercício 6: Delayed Messages com TTL
- Exercício 7: Idempotência e Deduplicação
- Exercício 8: Saga Pattern

## 👨‍💻 Autor

**Iago Gomes**
- GitHub: [@iagoomes](https://github.com/iagoomes)
- LinkedIn: [Iago Gomes](https://www.linkedin.com/in/deviagogomes)

⭐ Se este projeto te ajudou, deixe uma estrela no repositório!

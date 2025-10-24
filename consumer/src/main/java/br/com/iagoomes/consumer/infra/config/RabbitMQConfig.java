package br.com.iagoomes.consumer.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    @Value("${fixed-income.queue.name}")
    private String queue;
    @Value("${fixed-income.queue.exchange}")
    private String exchange;
    @Value("${fixed-income.queue.routing-key}")
    private String routingKey;
    @Value("${fixed-income.queue.dead-letter-queue}")
    private String queueDeadLetter;
    @Value("${fixed-income.queue.dead-letter-exchange}")
    private String exchangeDeadLetter;
    @Value("${fixed-income.queue.dead-letter-routing-key}")
    private String routingKeyDeadLetter;


    // Main Queue
    @Bean
    public Queue fixedIncomeQueue() {
        return QueueBuilder.durable(this.queue)
                .deadLetterExchange(this.exchangeDeadLetter)
                .deadLetterRoutingKey(this.routingKeyDeadLetter)
                .build();
    }

    // Main Exchange
    @Bean
    public DirectExchange fixedIncomeExchange() {
        return new DirectExchange(this.exchange);
    }

    // Bindings
    @Bean
    public Binding ordersBinding() {
        return BindingBuilder
                .bind(fixedIncomeQueue())
                .to(fixedIncomeExchange())
                .with(this.routingKey);
    }

    // Dead Letter Exchange (DLX)
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(this.exchangeDeadLetter);
    }

    // Dead Letter Queue (DLQ)
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(this.queueDeadLetter).build();
    }

    // Binding DLQ to DLX
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(this.routingKeyDeadLetter);
    }

    // Message Converter
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        return factory;
    }
}

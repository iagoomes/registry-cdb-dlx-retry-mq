package br.com.iagoomes.infra.mqprovider.producer;

import br.com.iagoomes.domain.dto.CdbRegistryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CdbRegistryProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${fixed-income.queue.exchange}")
    private String exchange;

    @Value("${fixed-income.queue.routing-key}")
    private String routingKey;

    public void sendCdbRegistryCreated(CdbRegistryDto cdbRegistry) {
        log.info("Sending CDB registry to RabbitMQ: {}", cdbRegistry);
        rabbitTemplate.convertAndSend(exchange, routingKey, cdbRegistry);
        log.info("CDB registry sent successfully");
    }
}
package br.com.iagoomes.consumer.infra.mqprovider.consume;

import br.com.iagoomes.consumer.domain.dto.CdbRegistryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CdbRegistryConsume {

    @RabbitListener(queues = "${fixed-income.queue.name}")
    public void handleCdbRegistryCreated(CdbRegistryDto cdbRegistry) {
        log.info("Processing CDB registry: {}", cdbRegistry);
        log.info("Registry ID: {}, Client: {}, Amount: {}, Duration: {} days, Interest Rate: {}%",
                cdbRegistry.getRegistryId(),
                cdbRegistry.getClientId(),
                cdbRegistry.getAmount(),
                cdbRegistry.getDurationDays(),
                cdbRegistry.getInterestRate());
    }
}

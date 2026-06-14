package com.lcerda.task.app.test_parnas.messaging;

import com.lcerda.task.app.test_parnas.config.RabbitMqConfig;
import com.lcerda.task.app.test_parnas.dto.OrderCreatedMessage;
import com.lcerda.task.app.test_parnas.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedListener {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitMqConfig.ORDER_CREATED_QUEUE)
    public void handleOrderCreated(OrderCreatedMessage message) {
        log.info(
                "Order created: orderId={}, customerName={}, totalAmount={}",
                message.getOrderId(),
                message.getCustomerName(),
                message.getTotalAmount()
        );

        orderService.markOrderAsProcessing(message.getOrderId());
    }
}

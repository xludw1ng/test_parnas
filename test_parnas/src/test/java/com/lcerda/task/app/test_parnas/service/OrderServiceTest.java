package com.lcerda.task.app.test_parnas.service;

import com.lcerda.task.app.test_parnas.config.RabbitMqConfig;
import com.lcerda.task.app.test_parnas.dto.CreateOrderItemRequest;
import com.lcerda.task.app.test_parnas.dto.CreateOrderRequest;
import com.lcerda.task.app.test_parnas.dto.OrderCreatedMessage;
import com.lcerda.task.app.test_parnas.dto.OrderResponse;
import com.lcerda.task.app.test_parnas.models.Order;
import com.lcerda.task.app.test_parnas.models.OrderStatus;
import com.lcerda.task.app.test_parnas.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrderSavesOrderAndPublishesMessage() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Test Customer",
                List.of(
                        new CreateOrderItemRequest("Coffee", 2, new BigDecimal("120.50")),
                        new CreateOrderItemRequest("Cake", 1, new BigDecimal("250.00"))
                )
        );

        UUID orderId = UUID.randomUUID();

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(orderId);
            return order;
        });

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.getId()).isEqualTo(orderId);
        assertThat(response.getCustomerName()).isEqualTo("Test Customer");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("491.00");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getCustomerName()).isEqualTo("Test Customer");
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(savedOrder.getItems()).hasSize(2);
        assertThat(savedOrder.getItems())
                .allSatisfy(item -> assertThat(item.getOrder()).isSameAs(savedOrder));

        ArgumentCaptor<OrderCreatedMessage> messageCaptor = ArgumentCaptor.forClass(OrderCreatedMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.ORDER_EXCHANGE),
                eq(RabbitMqConfig.ORDER_CREATED_ROUTING_KEY),
                messageCaptor.capture()
        );

        OrderCreatedMessage message = messageCaptor.getValue();
        assertThat(message.getOrderId()).isEqualTo(orderId);
        assertThat(message.getCustomerName()).isEqualTo("Test Customer");
        assertThat(message.getTotalAmount()).isEqualByComparingTo("491.00");
    }
}

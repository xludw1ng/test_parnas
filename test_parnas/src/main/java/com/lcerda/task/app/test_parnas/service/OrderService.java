package com.lcerda.task.app.test_parnas.service;

import com.lcerda.task.app.test_parnas.config.RabbitMqConfig;
import com.lcerda.task.app.test_parnas.dto.CreateOrderItemRequest;
import com.lcerda.task.app.test_parnas.dto.CreateOrderRequest;
import com.lcerda.task.app.test_parnas.dto.OrderCreatedMessage;
import com.lcerda.task.app.test_parnas.dto.OrderItemResponse;
import com.lcerda.task.app.test_parnas.dto.OrderResponse;
import com.lcerda.task.app.test_parnas.dto.UpdateOrderStatusRequest;
import com.lcerda.task.app.test_parnas.exception.ResourceNotFoundException;
import com.lcerda.task.app.test_parnas.models.Order;
import com.lcerda.task.app.test_parnas.models.OrderItem;
import com.lcerda.task.app.test_parnas.models.OrderStatus;
import com.lcerda.task.app.test_parnas.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerName(request.getCustomerName());
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.CREATED);

        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            OrderItem item = new OrderItem();
            item.setProductName(itemRequest.getProductName());
            item.setQuantity(itemRequest.getQuantity());
            item.setPrice(itemRequest.getPrice());
            item.setOrder(order);
            order.getItems().add(item);
        }

        Order savedOrder = orderRepository.save(order);
        BigDecimal totalAmount = calculateTotalAmount(savedOrder);

        OrderCreatedMessage message = OrderCreatedMessage.builder()
                .orderId(savedOrder.getId())
                .customerName(savedOrder.getCustomerName())
                .totalAmount(totalAmount)
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ORDER_EXCHANGE,
                RabbitMqConfig.ORDER_CREATED_ROUTING_KEY,
                message);

        return mapToOrderResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(OrderStatus status, Pageable pageable) {
        Page<Order> orders = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findAllByStatus(status, pageable);

        return orders.map(this::mapToOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        Order order = findOrderById(id);
        return mapToOrderResponse(order);
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID id, UpdateOrderStatusRequest request) {
        Order order = findOrderById(id);
        order.setStatus(request.getStatus());

        Order savedOrder = orderRepository.save(order);
        return mapToOrderResponse(savedOrder);
    }

    @Transactional
    public void markOrderAsProcessing(UUID id) {
        Order order = findOrderById(id);
        order.setStatus(OrderStatus.PROCESSING);
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateCustomerTotalAmount(String customerName) {
        return orderRepository.calculateTotalAmountByCustomerName(customerName);
    }

    private Order findOrderById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + id + " was not found"));
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::mapToOrderItemResponse)
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .customerName(order.getCustomerName())
                .orderDate(order.getOrderDate())
                .status(order.getStatus())
                .items(items)
                .totalAmount(calculateTotalAmount(order))
                .build();
    }

    private OrderItemResponse mapToOrderItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(calculateSubtotal(item))
                .build();
    }

    private BigDecimal calculateTotalAmount(Order order) {
        return order.getItems().stream()
                .map(this::calculateSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateSubtotal(OrderItem item) {
        return item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }
}

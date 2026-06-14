package com.lcerda.task.app.test_parnas.dto;

import com.lcerda.task.app.test_parnas.models.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private UUID id;
    private String customerName;
    private LocalDateTime orderDate;
    private OrderStatus status;
    private List<OrderItemResponse> items;
    private BigDecimal totalAmount;
}

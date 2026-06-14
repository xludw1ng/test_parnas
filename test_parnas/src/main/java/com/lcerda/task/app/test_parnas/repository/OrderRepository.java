package com.lcerda.task.app.test_parnas.repository;

import com.lcerda.task.app.test_parnas.models.Order;
import com.lcerda.task.app.test_parnas.models.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findAllByStatus(OrderStatus status, Pageable pageable);

    @Query(value = """
            SELECT COALESCE(SUM(oi.price * oi.quantity), 0)
            FROM orders o
            JOIN order_items oi ON oi.order_id = o.id
            WHERE o.customer_name = :customerName
            """, nativeQuery = true)
    BigDecimal calculateTotalAmountByCustomerName(@Param("customerName") String customerName);
}

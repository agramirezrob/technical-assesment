package com.b2b.orders.domain.model;

import java.util.List;

public record OrderReceived(String orderId, String clientId, List<OrderItem> items) {}

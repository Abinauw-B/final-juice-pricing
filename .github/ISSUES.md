# Repository Issues Log (2 Activity Perspectives)

## Issue #101: Spring Boot WebSocket Import Missing in PricingController
- **Status**: Closed (Resolved)
- **Type**: Bug Report
- **Description**: `PricingController` failed to compile due to missing import `org.springframework.messaging.simp.SimpMessagingTemplate`.
- **Resolution**: Added standard Spring STOMP messaging import without modifying existing controller architecture.

## Issue #102: Volume Deduction Calculation Missing in POSService Checkout Loop
- **Status**: Closed (Resolved)
- **Type**: Bug Report
- **Description**: Cart item processing loop attempted to reference `totalVolumeMl` without declaring it first.
- **Resolution**: Declared `int totalVolumeMl = cupSize * qty;` immediately after `qty` calculation to preserve atomic batch deductions.

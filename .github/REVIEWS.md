# Repository Code Reviews Log (2 Activity Perspectives)

## Code Review #301: Audit of PricingController & POSService Backend Changes
- **Reviewer**: Lead Architecture Reviewer
- **Target PR**: #201 (Fix Spring Boot Backend Compilation Errors)
- **Verdict**: Approved with Comments
- **Key Findings**:
  1. `SimpMessagingTemplate` properly imported from `org.springframework.messaging.simp`.
  2. `totalVolumeMl` calculation verified as `cupSize * qty`. Inventory batch deduction remains intact.
  3. No duplicate Maven dependencies added to `pom.xml`.

## Code Review #302: Audit of Dynamic Pricing Engine Explanations & Test Suite
- **Reviewer**: Senior Backend & QA Engineer
- **Target PR**: #202 (Standardize Dynamic Pricing Explanations & Test Setup)
- **Verdict**: Approved
- **Key Findings**:
  1. Explanation messages now consistently state flavour names (e.g., `MANGO`, `LEMON`).
  2. All 16 automated tests pass in under 15 seconds.
  3. Real-time WebSocket broadcasting verified on port 8088.

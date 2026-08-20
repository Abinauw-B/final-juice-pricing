# Code Review Guidelines & Standard Checklist

## Review Criteria
1. **Compilation & Syntax**: Ensure code compiles with zero errors using `mvnw clean package`.
2. **Architecture Preservation**: Verify WebSocket broadcasts, pessimistic locking, and dynamic pricing calculations are preserved.
3. **Test Coverage**: All 16 JUnit 5 suite tests must pass without failure.
4. **API Integrity**: Check `/api/health`, `/api/products`, and `/api/pricing/evaluate` outputs.
5. **Code Style & Imports**: Clean import declarations without duplicates or unused symbols.

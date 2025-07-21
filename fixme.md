# Moxxie Fix Me - Issues to Address

This document contains all the issues discovered during the comprehensive audit that require discussion or major changes before implementation.

## ✅ Completed Fixes (2025-07-21)

### 1. Tag Format Inconsistency ✅
**Fixed**: All documentation and code examples now consistently use dashes instead of colons.
- Updated all Java test files to use `env-prod`, `client-nixz` format
- Fixed examples in documentation files
- Updated OpenAPI schema examples

### 2. Cloud-Init Image Source Validation ✅
**Fixed**: Added custom validation to reject ISO paths with helpful error messages.
- Created `@ValidImageSource` annotation and `ImageSourceValidator`
- Applied to `CloudInitVMRequest` and `NodeTemplate`
- Provides clear error message: "For cloud-init VMs, use the format 'storage:vmid/base-vmid-disk-N'"
- Added comprehensive unit tests

### 3. VM Node Resolution Centralization ✅
**Fixed**: Created `VMLocatorService` to eliminate code duplication.
- Centralized VM node resolution logic
- Updated `TagService` and `ConsoleService` to use the new service
- Removed duplicate `getNodeForVM` and `findVM` methods
- Improved error handling with Optional returns

### 6. Authentication Migration ✅
**Fixed**: TagService already uses @AutoAuthenticate (was incorrectly listed as needing migration).
- Verified TagService is properly annotated with @AutoAuthenticate
- All methods use @AuthTicket annotation for parameters

## Major Refactoring Still Needed

### 4. Error Response Pattern Duplication
**Issue**: 22+ Resource classes have identical error handling patterns.

**Proposed Solution**: 
- Create global ExceptionMapper
- Standardize error response format
- Parse Proxmox errors for better messages

### 5. Service Layer Circular Dependencies
**Issue**: Services inject each other creating circular dependency risks.

**Examples**:
- VMService → TagService → VMService potential
- ConsoleService → VMService → ConsoleService potential

**Decision Required**: Establish clear service hierarchy.

## Configuration & Architecture Issues

### 7. Inconsistent Configuration Approach
**Issue**: Mix of @ConfigProperty and @ConfigMapping usage.

**Current State**:
- MoxxieConfig uses modern @ConfigMapping
- Some classes still use @ConfigProperty
- No validation annotations

**Recommendation**: Standardize on @ConfigMapping everywhere.

### 8. Missing Quarkus Features
**Issue**: Not using several beneficial Quarkus features.

**Missing**:
- Quarkus Cache (custom implementation instead)
- Metrics annotations (@Counted, @Timed)
- OpenTelemetry integration
- Dev Services for database

**Decision Required**: Which features to adopt in next version?

## Logging & Debugging Issues

### 9. No Request Correlation
**Issue**: Cannot trace requests across services and to Proxmox.

**Impact**: Makes debugging production issues very difficult.

**Proposed Solution**: 
- Add correlation ID filter
- Use MDC for context
- Pass correlation ID to Proxmox

### 10. Inconsistent Logger Usage
**Issue**: Mix of SLF4J and JBoss Logger, uppercase/lowercase naming.

**Files with Issues**:
- Most use `private static final Logger log` (SLF4J)
- 5 files use `private static final Logger LOG`
- AuditService uses JBoss Logger

**Decision Required**: Standardize on which logger?

## Testing & Development Experience

### 11. Integration Test Safety
**Issue**: Integration tests could accidentally affect production VMs.

**Current Mitigation**: 
- Uses TEST_PREFIX and tags
- Disabled by default
- Cleanup in @AfterEach

**Additional Safety Needed**?:
- Require explicit node whitelist?
- Add more safety checks?

### 12. Development Scripts Missing
**Issue**: No helper scripts for common development tasks.

**Suggested Scripts**:
- `dev-start.sh` - Start with live reload
- `test-api.sh` - Test API endpoints
- `clean-test-vms.sh` - Clean up test resources

## Performance & Scalability

### 13. Missing Performance Features
**Issue**: No systematic performance tracking.

**Missing**:
- Operation timing logs
- Slow query detection
- Resource pool exhaustion alerts

**Decision Required**: Add performance framework or wait for metrics?

### 14. Bulk Operation Limitations
**Issue**: Bulk operations don't support partial success well.

**Current**: All-or-nothing approach
**Better**: Report per-VM success/failure with continuation

## Security Considerations

### 15. No Authentication/Authorization
**Issue**: API has no authentication layer.

**Current**: Relies on network security
**Risk**: Anyone with network access has full control

**Timeline**: When to add auth? What type?

### 16. Sensitive Data in Logs
**Issue**: Passwords and tickets may appear in logs.

**Examples**:
- Form parameters logged by ProxmoxClientLoggingFilter
- Error messages may contain sensitive data

**Decision Required**: Add log sanitization?

## Questions for Team Discussion

1. **Service Architecture**: How to better structure services to avoid circular dependencies?

2. **Error Handling Strategy**: Implement global exception handling now or wait?

3. **Authentication Timeline**: When should we add API authentication?

4. **Logging Framework**: Standardize on SLF4J or JBoss Logger?

5. **Performance Monitoring**: Basic timing now or wait for full metrics?

6. **Test Safety**: Are current integration test safeguards sufficient?

7. **Quarkus Features**: Which additional features to adopt first?

8. **Documentation Priority**: Add missing docs or focus on code?

## Proposed Implementation Order

### Phase 1 ✅ (Completed 2025-07-21)
1. ✅ Fix tag format consistency in all documentation
2. ✅ Add cloud-init imageSource validation with helpful error
3. ✅ Complete @AutoAuthenticate verification
4. ✅ Create VMLocatorService to reduce duplication

### Phase 2 (Next Sprint)
1. Add global exception handler
2. Standardize logger usage
3. Add correlation IDs for request tracking
4. Create development helper scripts

### Phase 3 (Next Minor Version)
1. Add Quarkus Cache
2. Implement metrics
3. Add basic API authentication
4. Improve bulk operation handling

### Phase 4 (Future)
1. Full RBAC implementation
2. OpenTelemetry integration
3. Advanced performance monitoring
4. Service architecture refactoring

## Notes

- Original audit date: 2025-06-26
- Phase 1 completed: 2025-07-21
- Priority given to user-facing issues (tag format, errors)
- Consider creating GitHub issues for tracking remaining items
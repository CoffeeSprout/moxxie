# Moxxie Fix Me - Issues to Address

This document contains all the issues discovered during the comprehensive audit that require discussion or major changes before implementation.

## Critical Issues

### 1. Tag Format Inconsistency (CRITICAL)
**Issue**: Documentation states tags cannot contain colons but all examples use colons.

**Current State**:
- CLAUDE.md states: "Tags in Proxmox cannot contain colons. Always use dashes instead"
- But examples use: `env:prod`, `client:acme`, etc.
- README.md uses dashes correctly: `env-prod`, `client-nixz`

**Decision Required**: 
- Which format should we use? Colons or dashes?
- Need to update ALL documentation to be consistent
- May need migration path if changing existing deployments

**Recommendation**: Use dashes as stated in documentation, update all examples.

### 2. Cloud-Init Image Source Documentation
**Issue**: Critical requirement for imageSource format is buried in documentation.

**Current State**:
- Users must use `local-zfs:base-9002-disk-0` format, not ISO paths
- This causes "unable to parse directory volume name" errors
- Information is in a small note in API_EXAMPLES.md

**Decision Required**:
- Should we add validation to reject ISO paths with helpful error?
- Move this to prominent location in docs?

## Major Refactoring Needed

### 3. Code Duplication - VM Node Resolution
**Issue**: Multiple services implement their own way to find VM nodes.

**Files Affected**:
- TagService.java (getNodeForVM method)
- ConsoleService.java (findVM method)
- Several other services

**Proposed Solution**: Create VMLocatorService to centralize this logic.

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

### 6. Authentication Migration Incomplete
**Issue**: Some services still manually manage tickets instead of using @AutoAuthenticate.

**Services to Migrate**:
- TagService.java
- SDNService.java

**Decision Required**: Complete migration or document why these are exceptions?

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

1. **Tag Format Decision**: Colons or dashes? Need immediate decision.

2. **Service Architecture**: How to better structure services to avoid circular dependencies?

3. **Error Handling Strategy**: Implement global exception handling now or wait?

4. **Authentication Timeline**: When should we add API authentication?

5. **Logging Framework**: Standardize on SLF4J or JBoss Logger?

6. **Performance Monitoring**: Basic timing now or wait for full metrics?

7. **Breaking Changes**: Can we fix tag format without breaking existing users?

8. **Test Safety**: Are current integration test safeguards sufficient?

9. **Quarkus Features**: Which additional features to adopt first?

10. **Documentation Priority**: Fix examples first or add missing docs?

## Proposed Implementation Order

### Phase 1 (Immediate - Before Next Release)
1. Fix tag format consistency in all documentation
2. Add cloud-init imageSource validation with helpful error
3. Complete @AutoAuthenticate migration
4. Standardize logger usage

### Phase 2 (Next Sprint)
1. Implement VMLocatorService to reduce duplication
2. Add global exception handler
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

- All line numbers referenced are as of the audit date (2025-06-26)
- Some issues may already be partially addressed
- Priority should be given to user-facing issues (tag format, errors)
- Consider creating GitHub issues for tracking each item
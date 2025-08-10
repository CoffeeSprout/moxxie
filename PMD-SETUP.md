# PMD Static Analysis Setup

## Overview
PMD has been configured for the Moxxie project to provide static code analysis and maintain code quality standards. The analysis is **advisory only** and will not block builds.

## Configuration

### Maven Plugin
- **PMD Core Version**: 7.16.0 (latest stable release as of July 2025)
- **Maven Plugin Version**: 3.27.0 (latest version as of June 2025)
- **Java Target**: Java 21
- **Execution Phase**: `verify` phase (runs with `mvn verify`)
- **Failure Mode**: Advisory only (`failOnViolation=false`)

### Custom Ruleset
The project uses a custom ruleset (`pmd-ruleset.xml`) tailored for:
- Quarkus framework patterns
- CDI dependency injection
- REST API design
- Java 21 features

### Key Features

#### Enabled Rule Categories
1. **Best Practices** - Common Java best practices
2. **Code Style** - Naming conventions, formatting
3. **Design** - Complexity metrics, coupling, cohesion
4. **Error Prone** - Common bugs and mistakes
5. **Performance** - Performance anti-patterns
6. **Security** - Security vulnerabilities
7. **Multithreading** - Thread safety issues
8. **Documentation** - Code documentation standards

#### Custom Project Rules
- Use `ProxmoxException` instead of `RuntimeException`
- Use constants from `VMConstants` or `ProxmoxConstants` instead of magic numbers
- Use `@AuthTicket` annotation for authentication parameters

#### Excluded Rules
Some PMD rules are disabled as they conflict with our patterns:
- `GuardLogStatement` - Quarkus/JBoss logging handles this automatically
- `LawOfDemeter` - REST endpoints often need to chain calls
- `DataClass` - DTOs are intentionally data classes
- `TooManyMethods` - Resource classes can have many endpoints
- `AtLeastOneConstructor` - Not needed for CDI beans

## Usage

### Running PMD

```bash
# Quick analysis (generates XML report by default in PMD 7.16+)
./mvnw pmd:pmd

# View report
cat target/pmd.xml

# Run full build with PMD (advisory only)
./mvnw clean verify
```

### Convenience Scripts

```bash
# Full analysis with interactive report viewing
./scripts/pmd-check.sh

# Quick analysis of changed files only
./scripts/pmd-quick.sh
```

### CI/CD Integration
PMD runs automatically in GitHub Actions via `.github/workflows/pmd-analysis.yml`:
- Triggered on push to main/develop branches
- Triggered on pull requests
- Generates reports as workflow artifacts
- Comments on PRs with violation count

## Addressing PMD Findings

### Review Process
1. **Review the finding** - Understand why PMD flagged the code
2. **Fix if valid** - Most findings indicate real improvements
3. **Suppress if false positive** - Use annotations with justification
4. **Update ruleset** - Adjust rules that consistently give false positives

### Suppressing False Positives

```java
// Suppress with justification comment
@SuppressWarnings("PMD.AvoidDuplicateLiterals")  // These are distinct API endpoints
private static final String VM_PATH = "/api/v1/vms";
private static final String SNAPSHOT_PATH = "/api/v1/snapshots";

// Suppress multiple rules
@SuppressWarnings({"PMD.ExcessiveParameterList", "PMD.TooManyFields"})
public class ComplexConfiguration {
    // Complex configuration classes may need many parameters
}
```

## Complexity Thresholds

Current configured thresholds:
- **Cyclomatic Complexity**: 10 per method, 80 per class
- **NPath Complexity**: 200
- **Excessive Parameters**: 8
- **Excessive Public Count**: 45
- **Too Many Fields**: 20

## Benefits

1. **Early Bug Detection** - Catches common programming errors
2. **Code Consistency** - Enforces naming conventions and style
3. **Performance** - Identifies performance anti-patterns
4. **Security** - Detects potential security vulnerabilities
5. **Maintainability** - Flags overly complex code
6. **Documentation** - Encourages proper code documentation

## Future Improvements

1. **Gradual Enforcement** - As code quality improves, consider making some rules mandatory
2. **Custom Rules** - Add more project-specific rules as patterns emerge
3. **IDE Integration** - Configure IDE plugins for real-time feedback
4. **Metrics Tracking** - Track PMD metrics over time to measure improvement
5. **Team Training** - Share PMD findings in code reviews for team learning

## Troubleshooting

### PMD Processing Errors
If you see "PMD processing errors" in the output:
- These are usually from XPath rules that need adjustment for PMD 7
- The analysis still runs for other rules
- Check `target/pmd.xml` for detailed error messages

### Performance
- PMD uses an analysis cache for faster incremental runs
- Cache location: `target/pmd/pmd-cache`
- Clear cache if you suspect stale results: `rm -rf target/pmd`

### Rule Configuration
- All rule configurations are in `pmd-ruleset.xml`
- Test rule changes with: `./mvnw pmd:pmd -Dformat=text`
- Validate ruleset XML: `./mvnw pmd:check-rules`

## Resources

- [PMD Documentation](https://pmd.github.io/)
- [PMD Rule Reference](https://pmd.github.io/latest/pmd_rules_java.html)
- [PMD Best Practices](https://pmd.github.io/latest/pmd_userdocs_best_practices.html)
- [Writing Custom Rules](https://pmd.github.io/latest/pmd_userdocs_extending_writing_rules_intro.html)
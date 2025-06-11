# Phase 5: Production Readiness

## Overview
Prepare Moxxie for production deployment with monitoring, metrics, health checks, containerization, and operational documentation.

## Monitoring & Metrics

### Metrics Collection
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-micrometer</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
</dependency>
```

### Custom Metrics
```java
@ApplicationScoped
public class MoxxieMetrics {
    
    @Inject
    MeterRegistry registry;
    
    private Counter vmCreations;
    private Gauge availableCpu;
    private Timer proxmoxApiTimer;
    
    @PostConstruct
    void init() {
        vmCreations = Counter.builder("moxxie_vm_creations_total")
            .description("Total VM creations")
            .tag("instance", instanceId)
            .register(registry);
            
        availableCpu = Gauge.builder("moxxie_available_cpu_cores", this, 
            MoxxieMetrics::calculateAvailableCpu)
            .description("Available CPU cores")
            .tag("instance", instanceId)
            .register(registry);
    }
}
```

### Health Checks
```java
@Liveness
@ApplicationScoped
public class ProxmoxHealthCheck implements HealthCheck {
    
    @Inject
    ProxmoxClient proxmoxClient;
    
    @Override
    public HealthCheckResponse call() {
        try {
            proxmoxClient.getVersion();
            return HealthCheckResponse.up("proxmox-connection");
        } catch (Exception e) {
            return HealthCheckResponse.down("proxmox-connection", 
                Map.of("error", e.getMessage()));
        }
    }
}

@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {
    
    @Inject
    EntityManager em;
    
    @Override
    public HealthCheckResponse call() {
        try {
            em.createNativeQuery("SELECT 1").getSingleResult();
            return HealthCheckResponse.up("database");
        } catch (Exception e) {
            return HealthCheckResponse.down("database");
        }
    }
}
```

## Containerization

### Dockerfile
```dockerfile
FROM registry.access.redhat.com/ubi8/openjdk-21-runtime:latest

ENV LANGUAGE='en_US:en'
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager --enable-preview"

COPY target/quarkus-app/lib/ /deployments/lib/
COPY target/quarkus-app/*.jar /deployments/
COPY target/quarkus-app/app/ /deployments/app/
COPY target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
```

### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: moxxie-wsdc1
  labels:
    app: moxxie
    instance: wsdc1
spec:
  replicas: 2
  selector:
    matchLabels:
      app: moxxie
      instance: wsdc1
  template:
    metadata:
      labels:
        app: moxxie
        instance: wsdc1
    spec:
      containers:
      - name: moxxie
        image: registry.example.com/moxxie:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: MOXXIE_INSTANCE_ID
          value: "wsdc1"
        - name: QUARKUS_DATASOURCE_JDBC_URL
          value: "jdbc:postgresql://postgres:5432/moxxie_wsdc1"
        - name: OIDC_CLIENT_SECRET
          valueFrom:
            secretKeyRef:
              name: moxxie-secrets
              key: oidc-secret
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
        resources:
          requests:
            memory: "256Mi"
            cpu: "500m"
          limits:
            memory: "512Mi"
            cpu: "1000m"
```

## Logging Configuration
```properties
# Structured logging
quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3.}] (%t) %s%e%n
quarkus.log.console.json=true
quarkus.log.console.json.fields.instance-id.value=${moxxie.instance.id}
quarkus.log.console.json.fields.version.value=${quarkus.application.version}

# Log levels
quarkus.log.category."com.coffeesprout".level=INFO
quarkus.log.category."org.hibernate".level=WARN
```

## Performance Tuning
```properties
# Virtual thread configuration
quarkus.thread-pool.core-threads=2
quarkus.thread-pool.max-threads=200
quarkus.vertx.worker-pool-size=20

# Database connection pool
quarkus.datasource.jdbc.min-size=5
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.acquisition-timeout=10

# REST client tuning
quarkus.rest-client.proxmox-api.connection-pool-size=10
quarkus.rest-client.proxmox-api.connection-ttl=30S
```

## Operational Documentation

### Runbook
1. **Deployment Checklist**
   - Verify Proxmox connectivity
   - Database migrations completed
   - Keycloak client configured
   - Monitoring endpoints accessible

2. **Common Issues**
   - Proxmox certificate errors
   - Database connection pool exhaustion
   - Keycloak token expiration

3. **Performance Tuning**
   - Virtual thread pool sizing
   - Database query optimization
   - Cache configuration

### API Documentation
- OpenAPI spec at `/q/openapi`
- Swagger UI at `/q/swagger-ui`
- Postman collection in `docs/postman/`

## Testing Requirements
- Load testing with k6 or JMeter
- Chaos testing (network failures, restarts)
- Security scanning with OWASP ZAP
- Performance baseline establishment

## Deliverables
1. Production-ready container image
2. Kubernetes manifests
3. Monitoring dashboards (Grafana)
4. Operational runbook
5. Performance test results
6. Security scan report

## Success Criteria
- 99.9% uptime SLA capability
- Response times <500ms for 95th percentile
- Handles 100 concurrent requests
- Graceful degradation under load
- Zero security vulnerabilities (High/Critical)
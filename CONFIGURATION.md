# Moxxie Configuration Guide

Moxxie uses a simplified configuration system designed for single Proxmox cluster operation. All configuration can be done through environment variables or the `application.properties` file.

## Automatic Authentication

Moxxie now handles Proxmox authentication automatically. You no longer need to:
- Make separate authentication API calls
- Manage authentication tickets
- Pass authentication headers with each request

Simply configure your Proxmox credentials via environment variables, and Moxxie will:
- Authenticate on startup
- Automatically inject tickets into all API calls
- Refresh tickets before they expire (every 90 minutes)
- Retry failed requests with fresh tickets

## Environment Variables

All configuration options can be set via environment variables. The naming convention is:
- Replace dots (.) with underscores (_)
- Convert to uppercase
- Prefix with the configuration prefix

### Instance Configuration

| Environment Variable | Description | Default |
|---------------------|-------------|---------|
| `MOXXIE_INSTANCE_ID` | Unique identifier for this Moxxie instance | `moxxie-dev` |
| `MOXXIE_LOCATION` | Physical or logical location of this instance | `development` |
| `MOXXIE_VERSION` | Version of the Moxxie instance | `1.0.0-SNAPSHOT` |
| `MOXXIE_ENVIRONMENT` | Environment name (dev, staging, prod) | `development` |

### Proxmox Configuration

| Environment Variable | Description | Default |
|---------------------|-------------|---------|
| `MOXXIE_PROXMOX_URL` | Proxmox API URL | `https://10.0.0.10:8006/api2/json` |
| `MOXXIE_PROXMOX_USERNAME` | Proxmox username | `root@pam` |
| `MOXXIE_PROXMOX_PASSWORD` | Proxmox password | `changeme` |
| `MOXXIE_PROXMOX_VERIFY_SSL` | Whether to verify SSL certificates | `false` |
| `MOXXIE_PROXMOX_CONNECTION_TIMEOUT` | Connection timeout in seconds | `30` |
| `MOXXIE_PROXMOX_DEFAULT_STORAGE` | Default storage for VM creation | `local-lvm` |
| `MOXXIE_PROXMOX_DEFAULT_BRIDGE` | Default network bridge | `vmbr0` |

### API Configuration

| Environment Variable | Description | Default |
|---------------------|-------------|---------|
| `MOXXIE_API_AUTH_ENABLED` | Enable API key authentication | `false` |
| `MOXXIE_API_KEY` | API key for authentication (optional) | _(empty)_ |
| `MOXXIE_API_READ_ONLY` | Enable read-only mode | `false` |
| `MOXXIE_API_RATE_LIMIT` | Rate limit (requests per minute) | `60` |

## Example Usage

### Development Environment

```bash
export MOXXIE_INSTANCE_ID="moxxie-dev-1"
export MOXXIE_LOCATION="local-dev"
export MOXXIE_PROXMOX_URL="https://192.168.1.100:8006/api2/json"
export MOXXIE_PROXMOX_USERNAME="admin@pve"
export MOXXIE_PROXMOX_PASSWORD="your-password"

./mvnw quarkus:dev
```

### Production Environment

```bash
export MOXXIE_INSTANCE_ID="moxxie-prod-west-1"
export MOXXIE_LOCATION="datacenter-west"
export MOXXIE_ENVIRONMENT="production"
export MOXXIE_PROXMOX_URL="https://proxmox.prod.example.com:8006/api2/json"
export MOXXIE_PROXMOX_USERNAME="moxxie@pve"
export MOXXIE_PROXMOX_PASSWORD="${PROXMOX_PASSWORD}"
export MOXXIE_PROXMOX_VERIFY_SSL="true"
export MOXXIE_API_AUTH_ENABLED="true"
export MOXXIE_API_KEY="${API_SECRET_KEY}"
export MOXXIE_API_READ_ONLY="false"

java -jar target/moxxie-1.0.0-SNAPSHOT-runner.jar
```

### Docker Environment

```dockerfile
FROM registry.access.redhat.com/ubi8/openjdk-21:latest

ENV MOXXIE_INSTANCE_ID="moxxie-docker"
ENV MOXXIE_LOCATION="container"
ENV MOXXIE_PROXMOX_URL="https://proxmox:8006/api2/json"
ENV MOXXIE_PROXMOX_USERNAME="moxxie@pve"
# Password should be injected at runtime via secrets

COPY target/moxxie-*-runner.jar /app/moxxie.jar
CMD ["java", "-jar", "/app/moxxie.jar"]
```

### Kubernetes ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: moxxie-config
data:
  MOXXIE_INSTANCE_ID: "moxxie-k8s-1"
  MOXXIE_LOCATION: "kubernetes-cluster"
  MOXXIE_ENVIRONMENT: "production"
  MOXXIE_PROXMOX_URL: "https://proxmox.internal:8006/api2/json"
  MOXXIE_PROXMOX_USERNAME: "moxxie@pve"
  MOXXIE_PROXMOX_VERIFY_SSL: "true"
  MOXXIE_API_AUTH_ENABLED: "true"
  MOXXIE_API_RATE_LIMIT: "120"
```

## Configuration Priority

Configuration values are resolved in the following order (highest priority first):
1. System environment variables
2. `.env` file (if using Quarkus dev mode)
3. `application.properties` file
4. Default values in code

## Security Best Practices

1. **Never commit passwords or API keys** to version control
2. Use environment variables or secrets management for sensitive data
3. Enable SSL verification in production (`MOXXIE_PROXMOX_VERIFY_SSL=true`)
4. Use API key authentication for production deployments
5. Consider read-only mode for monitoring instances
6. Use dedicated Proxmox users with minimal required permissions

## Migrating from Multi-Site Configuration

If you were previously using the multi-site YAML configuration, migration is straightforward:

1. Choose the primary cluster from your YAML configuration
2. Set `MOXXIE_PROXMOX_URL` to the cluster's API URL
3. Set `MOXXIE_PROXMOX_USERNAME` and `MOXXIE_PROXMOX_PASSWORD` for authentication
4. Configure instance identification using `MOXXIE_INSTANCE_ID` and `MOXXIE_LOCATION`
5. Remove any YAML configuration files

The simplified configuration focuses on single-cluster operation, making it easier to deploy and manage multiple Moxxie instances, each connecting to its own Proxmox cluster.

## API Usage with Automatic Authentication

With automatic authentication, using the Moxxie API is straightforward:

```bash
# No authentication endpoint needed!
# Just make API calls directly:

# Get cluster information
curl http://localhost:8080/api/v1/proxmox/discover

# List all nodes
curl http://localhost:8080/api/v1/proxmox/nodes

# List all VMs
curl http://localhost:8080/api/v1/proxmox/vms

# Get instance info (no Proxmox auth needed)
curl http://localhost:8080/api/v1/info
```

### Authentication Flow

1. When Moxxie starts, it uses the configured credentials to authenticate with Proxmox
2. The authentication ticket is cached and automatically injected into all Proxmox API calls
3. If a call fails with 401 Unauthorized, Moxxie automatically refreshes the ticket and retries
4. Tickets are proactively refreshed every 90 minutes (before the 2-hour Proxmox timeout)

### Troubleshooting Authentication

If you see authentication errors:

1. Verify your credentials are correct:
   ```bash
   echo $MOXXIE_PROXMOX_USERNAME
   echo $MOXXIE_PROXMOX_PASSWORD
   ```

2. Test manual authentication with curl:
   ```bash
   curl -k -d "username=your-user@pve&password=your-password" \
        https://your-proxmox:8006/api2/json/access/ticket
   ```

3. Check Moxxie logs for detailed error messages:
   ```bash
   ./mvnw quarkus:dev
   # Look for messages from TicketManager and AuthService
   ```

4. Ensure your Proxmox user has appropriate permissions for the operations you're attempting
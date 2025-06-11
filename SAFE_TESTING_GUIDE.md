# Safe Testing Guide for Production Proxmox

This guide explains how to safely test Moxxie with your production Proxmox cluster.

## Safety Features

1. **Read-Only User**: Uses a Proxmox user with PVEAuditor role (read-only)
2. **Read-Only Mode**: Application configuration prevents write operations
3. **Local Only**: Binds to localhost to prevent external access
4. **No Write APIs**: VM creation/deletion endpoints are protected

## Setup Steps

### 1. Create Read-Only Proxmox User

On your Proxmox server (as root):

```bash
pveum user add moxxie-readonly@pve
pveum passwd moxxie-readonly@pve  # Set a secure password
pveum aclmod / -user moxxie-readonly@pve -role PVEAuditor
```

### 2. Run the Test Script

```bash
./test-safe-production.sh
```

This will:
- Prompt for your Proxmox server URL
- Build Moxxie
- Start it in safe-test mode

### 3. Test the API

Once running, you can:

1. **View API Documentation**: http://localhost:8080/swagger-ui/
2. **Check Instance Info**: `curl http://localhost:8080/api/v1/info`
3. **Authenticate** (using read-only credentials):
   ```bash
   curl -X POST http://localhost:8080/api/v1/proxmox/auth \
     -H "Content-Type: application/json" \
     -d '{"username":"moxxie-readonly@pve","password":"your-password"}'
   ```
4. **Discover Cluster** (read-only):
   ```bash
   curl http://localhost:8080/api/v1/proxmox/discover \
     -H "Authorization: <ticket-from-auth>"
   ```

## What's Safe to Test

✅ **Safe Operations**:
- Authentication
- Cluster discovery
- Listing nodes
- Listing VMs
- Viewing storage
- Health checks

❌ **Blocked Operations** (even if attempted):
- Creating VMs
- Deleting VMs
- Starting/Stopping VMs
- Modifying configurations

## Monitoring

Watch the logs for any blocked operations:
```
20:45:22 WARN  [co.co.se.ReadOnlyInterceptor] BLOCKED: Write operation 'createVM' attempted in read-only mode
```

## Next Steps

After testing with read-only access, you can:
1. Deploy to a dedicated test environment
2. Create a test Proxmox cluster
3. Use nested virtualization for isolated testing

## Troubleshooting

- **Connection refused**: Check Proxmox URL and firewall
- **401 Unauthorized**: Verify read-only user credentials
- **SSL errors**: The test profile allows self-signed certs (don't use in real production!)
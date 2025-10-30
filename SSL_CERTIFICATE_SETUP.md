# SSL Certificate Setup for Moxxie

This guide explains how to configure SSL/TLS certificates for secure communication between Moxxie and your Proxmox clusters.

## Overview

Moxxie supports two modes for SSL certificate validation:

1. **Development Mode** (default): Trusts all certificates - convenient for local development with self-signed certificates
2. **Production Mode**: Validates certificates using a PEM certificate bundle - secure for production deployments

## Quick Start

### Development Setup (Trust All Certificates)

For local development, Moxxie automatically trusts all SSL certificates:

```bash
# No additional configuration needed!
# Just set your Proxmox credentials
export MOXXIE_PROXMOX_PASSWORD=your-password

./mvnw quarkus:dev
```

### Production Setup (Certificate Validation)

For production deployments, follow these steps:

1. **Export certificates from your Proxmox clusters** (see below)
2. **Create a certificate bundle** (see below)
3. **Configure Moxxie** to use the bundle:

```bash
export MOXXIE_CERT_BUNDLE=/etc/moxxie/certs/proxmox-ca-bundle.pem
export MOXXIE_PROXMOX_PASSWORD=your-secure-password

java -jar moxxie-runner.jar
```

---

## Exporting Self-Signed Certificates from Proxmox

### Method 1: Using OpenSSL (Recommended)

Connect to any node in your Proxmox cluster and export the certificate:

```bash
# Export the certificate from Proxmox
openssl s_client -connect proxmox-hostname:8006 -showcerts </dev/null 2>/dev/null | \
  openssl x509 -outform PEM > proxmox-ca.pem

# Verify the certificate
openssl x509 -in proxmox-ca.pem -text -noout
```

### Method 2: From Proxmox Web UI

1. Log into the Proxmox web interface
2. Navigate to **Datacenter → Certificates**
3. Find the certificate currently in use
4. Copy the certificate content (including `-----BEGIN CERTIFICATE-----` and `-----END CERTIFICATE-----`)
5. Save to a file named `proxmox-ca.pem`

### Method 3: From Proxmox Server Directly

SSH into your Proxmox server:

```bash
# The certificate is typically located at:
sudo cat /etc/pve/local/pveproxy-ssl.pem
```

---

## Creating Certificate Bundles

### Single Datacenter

If you have one Proxmox cluster:

```bash
# Just copy the certificate
cp proxmox-ca.pem /etc/moxxie/certs/proxmox-ca-bundle.pem
```

### Multiple Datacenters (DC1 + DC2)

If you manage multiple Proxmox clusters (e.g., CaffeineStack DC1 and DC2):

```bash
# Export certificate from DC1
openssl s_client -connect dc1-proxmox.example.com:8006 -showcerts </dev/null 2>/dev/null | \
  openssl x509 -outform PEM > dc1-proxmox-ca.pem

# Export certificate from DC2
openssl s_client -connect dc2-proxmox.example.com:8006 -showcerts </dev/null 2>/dev/null | \
  openssl x509 -outform PEM > dc2-proxmox-ca.pem

# Combine into a single bundle
cat dc1-proxmox-ca.pem dc2-proxmox-ca.pem > proxmox-ca-bundle.pem

# Optionally, add comments to identify each certificate
echo "# DC1 Certificate" > /etc/moxxie/certs/proxmox-ca-bundle.pem
cat dc1-proxmox-ca.pem >> /etc/moxxie/certs/proxmox-ca-bundle.pem
echo "" >> /etc/moxxie/certs/proxmox-ca-bundle.pem
echo "# DC2 Certificate" >> /etc/moxxie/certs/proxmox-ca-bundle.pem
cat dc2-proxmox-ca.pem >> /etc/moxxie/certs/proxmox-ca-bundle.pem
```

### Certificate Bundle Format

The PEM bundle should contain one or more certificates in this format:

```
-----BEGIN CERTIFICATE-----
MIIDXTCCAkWgAwIBAgIJAK... (DC1 certificate content)
...
-----END CERTIFICATE-----

-----BEGIN CERTIFICATE-----
MIIDYTCCAkmgAwIBAgIJAL... (DC2 certificate content)
...
-----END CERTIFICATE-----
```

---

## Configuration

### Environment Variables

```bash
# Required in production
export MOXXIE_CERT_BUNDLE=/etc/moxxie/certs/proxmox-ca-bundle.pem

# Optional - defaults to false in production
# export MOXXIE_PROXMOX_VERIFY_SSL=true
```

### Per-Datacenter Configuration

For managing multiple datacenters, you can use different certificate bundles per instance:

**DC1 Instance:**
```bash
export MOXXIE_LOCATION_DATACENTER=dc1
export MOXXIE_PROXMOX_URL=https://dc1-proxmox.example.com:8006/api2/json
export MOXXIE_CERT_BUNDLE=/etc/moxxie/certs/dc1-proxmox.pem
```

**DC2 Instance:**
```bash
export MOXXIE_LOCATION_DATACENTER=dc2
export MOXXIE_PROXMOX_URL=https://dc2-proxmox.example.com:8006/api2/json
export MOXXIE_CERT_BUNDLE=/etc/moxxie/certs/dc2-proxmox.pem
```

Or use a combined bundle for both:
```bash
export MOXXIE_CERT_BUNDLE=/etc/moxxie/certs/proxmox-ca-bundle.pem
```

---

## Verification

### Test Certificate Bundle

Verify your certificate bundle works with OpenSSL:

```bash
# Test connection with custom CA bundle
openssl s_client -connect proxmox-hostname:8006 \
  -CAfile /etc/moxxie/certs/proxmox-ca-bundle.pem \
  -showcerts </dev/null

# Should see "Verify return code: 0 (ok)"
```

### Test with Moxxie

Start Moxxie and check the logs:

```bash
# Enable debug logging
export QUARKUS_LOG_CATEGORY__COM_COFFEESPROUT__LEVEL=DEBUG

java -jar moxxie-runner.jar
```

Look for successful authentication messages:
```
INFO  [com.coffeesprout.service.TicketManager] Successfully authenticated with Proxmox on startup
INFO  [com.coffeesprout.service.ConfigValidator] ✓ Production configuration validated successfully
```

If SSL verification fails, you'll see:
```
ERROR [com.coffeesprout.service.TicketManager] Failed to authenticate on startup
javax.net.ssl.SSLHandshakeException: ...
```

---

## Troubleshooting

### Problem: SSL Certificate Verification Failed

**Error:**
```
javax.net.ssl.SSLHandshakeException: PKIX path building failed
```

**Solutions:**

1. **Verify certificate format:**
   ```bash
   openssl x509 -in proxmox-ca.pem -text -noout
   ```
   Should display certificate details without errors.

2. **Check certificate bundle path:**
   ```bash
   ls -l /etc/moxxie/certs/proxmox-ca-bundle.pem
   cat /etc/moxxie/certs/proxmox-ca-bundle.pem
   ```
   File should exist and contain valid PEM certificates.

3. **Test certificate manually:**
   ```bash
   curl --cacert /etc/moxxie/certs/proxmox-ca-bundle.pem \
     https://proxmox-hostname:8006/api2/json
   ```

4. **Temporarily disable verification to confirm it's a certificate issue:**
   ```bash
   export MOXXIE_PROXMOX_VERIFY_SSL=false
   ```
   If this works, the problem is with the certificate setup.

### Problem: Certificate Expired

**Error:**
```
javax.net.ssl.SSLHandshakeException: NotAfter: ...
```

**Solution:**

1. Renew the Proxmox certificate:
   ```bash
   # On Proxmox server
   pvecm updatecerts
   systemctl restart pveproxy
   ```

2. Re-export the certificate (see methods above)

3. Update the certificate bundle

### Problem: Wrong Certificate in Bundle

**Symptom:** SSL verification fails even though bundle is configured

**Solution:**

Verify the certificate matches the server:

```bash
# Get certificate from server
openssl s_client -connect proxmox-hostname:8006 -showcerts </dev/null 2>/dev/null | \
  openssl x509 -fingerprint -noout

# Compare with bundle
openssl x509 -in /etc/moxxie/certs/proxmox-ca-bundle.pem -fingerprint -noout
```

Fingerprints should match.

### Problem: Multiple Certificates, Can't Tell Which is Which

**Solution:**

Add comments and extract certificate details:

```bash
# View certificate subject and issuer
for cert in dc1-proxmox-ca.pem dc2-proxmox-ca.pem; do
  echo "=== $cert ==="
  openssl x509 -in $cert -noout -subject -issuer -dates
  echo
done
```

---

## Security Best Practices

### Production Deployments

1. ✅ **Always enable SSL verification** in production
2. ✅ **Store certificate bundles in a secure location** (e.g., `/etc/moxxie/certs/`)
3. ✅ **Set appropriate file permissions:**
   ```bash
   sudo chmod 644 /etc/moxxie/certs/proxmox-ca-bundle.pem
   sudo chown moxxie:moxxie /etc/moxxie/certs/proxmox-ca-bundle.pem
   ```
4. ✅ **Use strong passwords** - never use default passwords
5. ✅ **Rotate certificates** when they expire

### Development Environments

1. ⚠️ **Trust-all mode is OK** for local development
2. ⚠️ **Never use trust-all** in staging or production
3. ⚠️ **Keep dev and prod configurations separate**

---

## Transitioning from Self-Signed to CA-Signed Certificates

If you plan to move from self-signed certificates to CA-signed certificates:

1. **Install CA-signed certificate** on Proxmox:
   ```bash
   # On Proxmox server
   cp your-ca-cert.pem /etc/pve/local/pveproxy-ssl.pem
   cp your-ca-key.pem /etc/pve/local/pveproxy-ssl.key
   systemctl restart pveproxy
   ```

2. **Update Moxxie configuration:**
   ```bash
   # Option 1: Remove MOXXIE_CERT_BUNDLE to use system CA store
   unset MOXXIE_CERT_BUNDLE

   # Option 2: Update bundle with new CA certificate
   export MOXXIE_CERT_BUNDLE=/etc/moxxie/certs/new-ca-bundle.pem
   ```

3. **Verify:**
   ```bash
   curl https://proxmox-hostname:8006/api2/json
   # Should work without --insecure flag
   ```

---

## Related Documentation

- [Configuration Guide](./CONFIGURATION.md) - Complete configuration reference
- [Production Deployment Guide](./PRODUCTION_DEPLOYMENT.md) - Production setup instructions
- [Security Hardening Guide](./SECURITY.md) - Security best practices

---

## Getting Help

If you encounter issues with SSL certificate setup:

1. Check Moxxie logs for detailed error messages
2. Verify certificate format and validity with OpenSSL
3. Test connection manually with `curl` or `openssl s_client`
4. Report issues at: https://github.com/CoffeeSprout/moxxie/issues

---

**Pro Tip:** Keep your certificate bundles in version control (except for private keys!) and document which certificate belongs to which datacenter for easier management.

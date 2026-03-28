# AgriShield Private VPS Deployment Guide

**Version:** 1.0  
**Date:** March 28, 2026  
**Target:** Ubuntu 22.04 LTS on Private VPS  
**Architecture:** Java 25 + Tomcat 9.1 + Nginx + PostgreSQL 16 + Redis 7

---

## Prerequisites

- Private Ubuntu 22.04 LTS VPS (minimum 4GB RAM, 2 CPU cores)
- Domain name: `https://Agrishield.com` (DNS configured)
- Root or sudo-enabled SSH access
- SSL certificate for domain (auto-generate via Let's Encrypt)

---

## Quick Start (Automated)

For a fully automated deployment on a fresh Ubuntu VPS:

```bash
# 1. SSH into your VPS
ssh root@your-vps-ip

# 2. Download and run the automated deployment script
curl -fsSL https://raw.githubusercontent.com/your-repo/agrishield/main/deploy.sh | bash

# 3. Follow the on-screen prompts for:
#    - Google OAuth Client ID
#    - Twilio Account SID, Auth Token, Phone Number
#    - Admin email for initial account
#    - PostgreSQL password
#    - Redis configuration

# 4. Test the deployment
curl https://Agrishield.com/api/auth/me
# Should return: {"authenticated":false, "redirect":"/login"}
```

---

## Manual Deployment (Step-by-Step)

### Step 1: System Setup

```bash
# Update system packages
sudo apt update && sudo apt upgrade -y

# Install required tools
sudo apt install -y \
  openjdk-25-jdk \
  maven \
  git \
  nginx \
  postgresql-16 \
  redis-server \
  certbot \
  python3-certbot-nginx \
  curl \
  wget

# Verify Java 25 installation
java -version
# Expected: openjdk version "25" ...

# Create application user
sudo useradd -m -s /bin/bash agrishield
sudo usermod -aG sudo agrishield
```

### Step 2: Clone Repository

```bash
# Switch to agrishield user
sudo -u agrishield bash

# Create app directory
mkdir -p /home/agrishield/app
cd /home/agrishield/app

# Clone the repository (replace with your GitHub URL)
git clone https://github.com/your-repo/agrishield.git .

# Build the WAR file
cd /home/agrishield/app/agrishield
mvn clean package -DskipTests

# Expected output: BUILD SUCCESS
```

### Step 3: Database Setup

```bash
# Start PostgreSQL
sudo systemctl start postgresql
sudo systemctl enable postgresql

# Create database and user
sudo -u postgres psql -c "CREATE DATABASE agrishield;"
sudo -u postgres psql -c "CREATE USER agrishield_user WITH PASSWORD 'your-secure-password';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE agrishield TO agrishield_user;"

# Run migrations via Flyway (embedded in WAR)
cd /home/agrishield/app/agrishield
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/agrishield" \
SPRING_DATASOURCE_USERNAME="agrishield_user" \
SPRING_DATASOURCE_PASSWORD="your-secure-password" \
mvn flyway:migrate
```

### Step 4: Redis Setup

```bash
# Start Redis
sudo systemctl start redis-server
sudo systemctl enable redis-server

# Verify Redis is running
redis-cli ping
# Expected: PONG
```

### Step 5: Environment Configuration

Create `/home/agrishield/.agrishield.env`:

```bash
# Java & Runtime
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
JAVA_OPTS="-Xmx2G -Xms1G -Djava.net.preferIPv4Stack=true"

# Application
APP_HOME=/home/agrishield/app/agrishield
CONTEXT_PATH=/

# Database
DB_URL="jdbc:postgresql://localhost:5432/agrishield"
DB_USER="agrishield_user"
DB_PASSWORD="your-secure-password"

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# OAuth
GOOGLE_CLIENT_ID="285893124141-7chp3u04870firvqu08pv1k0dmva9s1g.apps.googleusercontent.com"
APPLE_CLIENT_ID="" # Not used - removed per user request

# SMS (Twilio)
TWILIO_ENABLED=true
TWILIO_ACCOUNT_SID="your-twilio-account-sid"
TWILIO_AUTH_TOKEN="your-twilio-auth-token"
TWILIO_PHONE_NUMBER="+1234567890" # Your Twilio phone number

# User Phone Numbers (for password reset SMS)
USER_PHONE_admin_at_agrishield_tz="+255700000001"
USER_PHONE_farmer_at_agrishield_tz="+255700000002"

# Auth Configuration
AUTH_SOCIAL_MODE="live" # or "demo"
AUTH_DEBUG_RESET_CODE="false" # Set to "false" in production

# Deployment
DOMAIN="Agrishield.com"
ENVIRONMENT="production"
LOG_LEVEL="INFO"
```

### Step 6: Tomcat Setup

```bash
# Create tomcat directory
sudo mkdir -p /opt/tomcat-agrishield
sudo chown -R agrishield:agrishield /opt/tomcat-agrishield

# Download and extract TomEE (includes Tomcat 9.1)
cd /opt/tomcat-agrishield
sudo -u agrishield wget https://archive.apache.org/dist/tomee/tomee-9.1.1/apache-tomee-9.1.1-webprofile.tar.gz
sudo -u agrishield tar -xzf apache-tomee-9.1.1-webprofile.tar.gz
sudo -u agrishield rm apache-tomee-9.1.1-webprofile.tar.gz

# Deploy WAR file
sudo cp /home/agrishield/app/agrishield/target/agrishield.war \
  /opt/tomcat-agrishield/apache-tomee-9.1.1/webapps/ROOT.war

# Create setenv.sh to load environment variables
cat > /opt/tomcat-agrishield/apache-tomee-9.1.1/bin/setenv.sh << 'EOF'
#!/bin/bash
source /home/agrishield/.agrishield.env
export JAVA_HOME
export JAVA_OPTS
export DB_URL DB_USER DB_PASSWORD
export REDIS_HOST REDIS_PORT
export GOOGLE_CLIENT_ID APPLE_CLIENT_ID
export TWILIO_ENABLED TWILIO_ACCOUNT_SID TWILIO_AUTH_TOKEN TWILIO_PHONE_NUMBER
export AUTH_SOCIAL_MODE AUTH_DEBUG_RESET_CODE
EOF

chmod +x /opt/tomcat-agrishield/apache-tomee-9.1.1/bin/setenv.sh
```

### Step 7: Nginx Reverse Proxy

Create `/etc/nginx/sites-available/agrishield`:

```nginx
# HTTP to HTTPS redirect
server {
    listen 80;
    server_name Agrishield.com www.Agrishield.com;
    return 301 https://$server_name$request_uri;
}

# HTTPS server
server {
    listen 443 ssl http2;
    server_name Agrishield.com www.Agrishield.com;

    # SSL certificates (auto-generated by Let's Encrypt)
    ssl_certificate /etc/letsencrypt/live/Agrishield.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/Agrishield.com/privkey.pem;

    # SSL configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # Root path
    root /var/www/agrishield;
    index index.html;

    # Static files (React frontend)
    location / {
        try_files $uri $uri/ /index.html;
        expires 1h;
    }

    # Static assets (long cache)
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # API proxy to Tomcat
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 90;
    }

    # Health check endpoint
    location /health {
        access_log off;
        proxy_pass http://localhost:8080/health;
    }
}
```

Enable the site:

```bash
sudo ln -s /etc/nginx/sites-available/agrishield /etc/nginx/sites-enabled/
sudo nginx -t  # Test configuration
sudo systemctl restart nginx
```

### Step 8: SSL Certificate Setup

```bash
# Generate Let's Encrypt certificate
sudo certbot certonly --standalone -d Agrishield.com -d www.Agrishield.com

# Auto-renewal
sudo systemctl enable certbot.timer
sudo systemctl start certbot.timer

# Verify auto-renewal
sudo certbot renew --dry-run
```

### Step 9: Start Tomcat

```bash
# Create systemd service
sudo tee /etc/systemd/system/tomcat-agrishield.service > /dev/null << 'EOF'
[Unit]
Description=Apache TomEE for AgriShield
After=network.target postgresql.service redis.service

[Service]
Type=forking
User=agrishield
Group=agrishield
WorkingDirectory=/opt/tomcat-agrishield/apache-tomee-9.1.1

ExecStart=/opt/tomcat-agrishield/apache-tomee-9.1.1/bin/startup.sh
ExecStop=/opt/tomcat-agrishield/apache-tomee-9.1.1/bin/shutdown.sh

# Environment
EnvironmentFile=/home/agrishield/.agrishield.env

Restart=on-failure
RestartSec=10

StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd and start service
sudo systemctl daemon-reload
sudo systemctl enable tomcat-agrishield
sudo systemctl start tomcat-agrishield

# Check service status
sudo systemctl status tomcat-agrishield
```

### Step 10: Copy React Frontend

```bash
# Build React frontend
cd /home/agrishield/app/agrishield/frontend
npm install
npm run build

# Copy to Nginx
sudo cp -r dist/* /var/www/agrishield/
sudo chown -R www-data:www-data /var/www/agrishield
```

---

## Verification & Testing

### Health Checks

```bash
# Tomcat health
curl http://localhost:8080/health

# Nginx
curl https://Agrishield.com/health

# API endpoints
curl -X POST https://Agrishield.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@agrishield.tz","password":"Admin#123","totpCode":"123456"}'

# Expected response:
# {
#   "id": "...",
#   "fullName": "Administrator",
#   "email": "admin@agrishield.tz",
#   "role": "admin",
#   "authProvider": "local"
# }
```

### Password Reset Test

```bash
# Request reset code (debug mode returns preview code)
curl -X POST https://Agrishield.com/api/auth/password/forgot \
  -H "Content-Type: application/json" \
  -d '{"identifier":"farmer@agrishield.tz"}'

# Check logs for SMS sent status
sudo tail -f /opt/tomcat-agrishield/apache-tomee-9.1.1/logs/catalina.out

# Reset password with code
curl -X POST https://Agrishield.com/api/auth/password/reset \
  -H "Content-Type: application/json" \
  -d '{"code":"123456","newPassword":"NewPass#123"}'
```

### Google OAuth Test

Navigate to `https://Agrishield.com/login` and:
1. Click "Sign in with Google"
2. Authenticate with your Google account
3. Should redirect to dashboard on successful verification

---

## Monitoring & Maintenance

### Log Files

```bash
# Tomcat logs
tail -f /opt/tomcat-agrishield/apache-tomee-9.1.1/logs/catalina.out

# Nginx logs
tail -f /var/log/nginx/error.log
tail -f /var/log/nginx/access.log

# System journal
sudo journalctl -u tomcat-agrishield -f
```

### Database Backups

```bash
# Automatic daily backup
sudo crontab -e

# Add line:
0 2 * * * /home/agrishield/backup-db.sh

# Create backup script at /home/agrishield/backup-db.sh:
#!/bin/bash
BACKUP_DIR="/home/agrishield/backups"
mkdir -p $BACKUP_DIR
pg_dump -U agrishield_user -h localhost agrishield | gzip > $BACKUP_DIR/backup_$(date +%Y%m%d_%H%M%S).sql.gz
# Keep only last 7 days
find $BACKUP_DIR -mtime +7 -delete
```

### Performance Tuning

**PostgreSQL** (`/etc/postgresql/16/main/postgresql.conf`):
```
shared_buffers = 512MB
effective_cache_size = 2GB
maintenance_work_mem = 256MB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
random_page_cost = 1.1
effective_io_concurrency = 200
work_mem = 1GB
min_wal_size = 2GB
max_wal_size = 4GB
```

**Redis** (`/etc/redis/redis.conf`):
```
maxmemory 1gb
maxmemory-policy allkeys-lru
timeout 300
tcp-keepalive 60
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Tomcat won't start | Check `JAVA_HOME` is set correctly; verify database credentials; check logs |
| Nginx 502 Bad Gateway | Verify Tomcat is running on port 8080; check proxy configuration |
| Database connection fails | Verify PostgreSQL is running; check credentials in `.agrishield.env` |
| SMS not sending | Verify Twilio credentials; check `AUTH_DEBUG_RESET_CODE=true` to see preview code |
| SSL certificate issues | Run `sudo certbot renew --force-renewal`; verify domain DNS |

---

## Rollback & Disaster Recovery

### Quick Rollback (if deployment fails)

```bash
# Stop Tomcat
sudo systemctl stop tomcat-agrishield

# Restore previous WAR
sudo cp /home/agrishield/backups/agrishield.war.bak \
  /opt/tomcat-agrishield/apache-tomee-9.1.1/webapps/ROOT.war

# Restart
sudo systemctl start tomcat-agrishield
```

### Database Restore

```bash
# Restore from backup
zcat /home/agrishield/backups/backup_20260328_020000.sql.gz | \
  psql -U agrishield_user -h localhost agrishield
```

---

## Security Hardening

- [ ] Enable firewall: `sudo ufw enable; sudo ufw allow 22,80,443/tcp`
- [ ] Set up SSH key authentication; disable password login
- [ ] Enable automated security updates: `sudo apt install unattended-upgrades`
- [ ] Setup fail2ban for brute-force protection
- [ ] Rotate database passwords regularly
- [ ] Enable PostgreSQL query logging for audit
- [ ] Setup monitoring/alerting (Prometheus + Grafana)

---

## Production Checklist

- [ ] Domain DNS configured and pointing to VPS IP
- [ ] SSL certificate generated and auto-renewal enabled
- [ ] Database backups automated
- [ ] Environment variables configured (`.agrishield.env`)
- [ ] Twilio credentials loaded
- [ ] Google OAuth client ID configured
- [ ] Health checks passing
- [ ] Monitoring/logging configured
- [ ] Firewall rules applied
- [ ] Admin account created and TOTP enabled

---

## Support & Resources

- **Repository**: [Your GitHub URL]
- **Documentation**: [Your Docs URL]
- **Issues**: [GitHub Issues]
- **Email**: [Your Support Email]

---

**Last Updated**: March 28, 2026  
**Next Review**: April 28, 2026

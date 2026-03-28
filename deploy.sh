#!/bin/bash
set -e

# AgriShield Automated Deployment Script for Ubuntu 22.04 LTS
# Run with: curl -fsSL https://raw.githubusercontent.com/your-repo/agrishield/main/deploy.sh | bash

DOMAIN="${1:-Agrishield.com}"
APP_HOME="/home/agrishield/app"
TOMCAT_HOME="/opt/tomcat-agrishield/apache-tomee-9.1.1"
DB_USER="agrishield_user"

echo "========================================"
echo "AgriShield Deployment Script"
echo "========================================"
echo "Domain: $DOMAIN"
echo "App Home: $APP_HOME"
echo "Tomcat Home: $TOMCAT_HOME"
echo ""

# Check if running as root
if [[ $EUID -ne 0 ]]; then
   echo "This script must be run as root"
   exit 1
fi

# Step 1: Update system
echo "[1/10] Updating system packages..."
apt update && apt upgrade -y > /dev/null 2>&1
echo "✓ System updated"

# Step 2: Install dependencies
echo "[2/10] Installing dependencies..."
apt install -y \
  openjdk-25-jdk \
  maven \
  git \
  nginx \
  postgresql-16 \
  redis-server \
  certbot \
  python3-certbot-nginx \
  postgresql-contrib \
  > /dev/null 2>&1
echo "✓ Dependencies installed"

# Step 3: Create application user
echo "[3/10] Setting up application user..."
if ! id -u agrishield > /dev/null 2>&1; then
  useradd -m -s /bin/bash agrishield
  usermod -aG sudo agrishield
else
  echo "  User 'agrishield' already exists"
fi
echo "✓ Application user ready"

# Step 4: Clone repository
echo "[4/10] Cloning repository..."
if [ ! -d "$APP_HOME" ]; then
  sudo -u agrishield mkdir -p "$APP_HOME"
  cd "$APP_HOME"
  sudo -u agrishield git clone https://github.com/your-repo/agrishield.git .
else
  echo "  Repository already cloned at $APP_HOME"
fi
echo "✓ Repository ready"

# Step 5: Build WAR
echo "[5/10] Building WAR file (this may take 2-3 minutes)..."
cd "$APP_HOME/agrishield"
sudo -u agrishield mvn clean package -DskipTests -q
echo "✓ WAR file built successfully"

# Step 6: Database setup
echo "[6/10] Configuring PostgreSQL..."
systemctl start postgresql
systemctl enable postgresql > /dev/null 2>&1

# Prompt for database password
read -sp "Enter secure password for $DB_USER (or press Enter for auto-generated): " DB_PASSWORD
if [ -z "$DB_PASSWORD" ]; then
  DB_PASSWORD=$(openssl rand -base64 16)
  echo ""
  echo "  Auto-generated password: $DB_PASSWORD"
else
  echo ""
fi

sudo -u postgres psql -c "CREATE DATABASE IF NOT EXISTS agrishield;" > /dev/null 2>&1
sudo -u postgres psql -c "DROP USER IF EXISTS $DB_USER;" > /dev/null 2>&1
sudo -u postgres psql -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';" > /dev/null 2>&1
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE agrishield TO $DB_USER;" > /dev/null 2>&1

echo "✓ PostgreSQL configured"
echo "  Database: agrishield"
echo "  User: $DB_USER"
echo "  Password: $DB_PASSWORD (save this!)"

# Step 7: Redis setup
echo "[7/10] Setting up Redis..."
systemctl start redis-server
systemctl enable redis-server > /dev/null 2>&1
echo "✓ Redis running"

# Step 8: Collect credentials
echo ""
echo "========================================"
echo "CREDENTIALS & CONFIGURATION"
echo "========================================"
read -p "Enter your Google OAuth Client ID: " GOOGLE_CLIENT_ID
read -p "Enter Twilio Account SID: " TWILIO_ACCOUNT_SID
read -p "Enter Twilio Auth Token: " TWILIO_AUTH_TOKEN
read -p "Enter Twilio Phone Number (e.g., +1234567890): " TWILIO_PHONE_NUMBER

# Step 9: Create environment file
echo "[8/10] Creating environment configuration..."
cat > /home/agrishield/.agrishield.env << EOF
# Java & Runtime
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
JAVA_OPTS="-Xmx2G -Xms1G -Djava.net.preferIPv4Stack=true"

# Database
DB_URL="jdbc:postgresql://localhost:5432/agrishield"
DB_USER="$DB_USER"
DB_PASSWORD="$DB_PASSWORD"

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# OAuth
GOOGLE_CLIENT_ID="$GOOGLE_CLIENT_ID"

# SMS (Twilio)
TWILIO_ENABLED=true
TWILIO_ACCOUNT_SID="$TWILIO_ACCOUNT_SID"
TWILIO_AUTH_TOKEN="$TWILIO_AUTH_TOKEN"
TWILIO_PHONE_NUMBER="$TWILIO_PHONE_NUMBER"

# Auth
AUTH_SOCIAL_MODE="live"
AUTH_DEBUG_RESET_CODE="false"

# Deployment
DOMAIN="$DOMAIN"
ENVIRONMENT="production"
LOG_LEVEL="INFO"
EOF

chown agrishield:agrishield /home/agrishield/.agrishield.env
chmod 600 /home/agrishield/.agrishield.env
echo "✓ Environment configuration saved"

# Step 10: Setup Tomcat
echo "[9/10] Setting up TomEE/Tomcat..."
mkdir -p /opt/tomcat-agrishield
chown -R agrishield:agrishield /opt/tomcat-agrishield

cd /opt/tomcat-agrishield
sudo -u agrishield wget -q https://archive.apache.org/dist/tomee/tomee-9.1.1/apache-tomee-9.1.1-webprofile.tar.gz
sudo -u agrishield tar -xzf apache-tomee-9.1.1-webprofile.tar.gz
sudo -u agrishield rm apache-tomee-9.1.1-webprofile.tar.gz

# Create setenv.sh
cat > $TOMCAT_HOME/bin/setenv.sh << 'ENVFILE'
#!/bin/bash
source /home/agrishield/.agrishield.env
export JAVA_HOME JAVA_OPTS
export DB_URL DB_USER DB_PASSWORD REDIS_HOST REDIS_PORT
export GOOGLE_CLIENT_ID TWILIO_ENABLED TWILIO_ACCOUNT_SID TWILIO_AUTH_TOKEN TWILIO_PHONE_NUMBER
export AUTH_SOCIAL_MODE AUTH_DEBUG_RESET_CODE ENVIRONMENT LOG_LEVEL
ENVFILE

chmod +x $TOMCAT_HOME/bin/setenv.sh

# Deploy WAR
cp "$APP_HOME/agrishield/target/agrishield.war" "$TOMCAT_HOME/webapps/ROOT.war"

# Create systemd service
cat > /etc/systemd/system/tomcat-agrishield.service << 'SERVICEFILE'
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

EnvironmentFile=/home/agrishield/.agrishield.env
Restart=on-failure
RestartSec=10

StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
SERVICEFILE

systemctl daemon-reload

echo "✓ TomEE configured"

# Step 11: Setup Nginx + SSL
echo "[10/10] Configuring Nginx and SSL..."

mkdir -p /var/www/agrishield
chown www-data:www-data /var/www/agrishield

# Nginx config
cat > /etc/nginx/sites-available/agrishield << NGINXFILE
server {
    listen 80;
    server_name $DOMAIN www.$DOMAIN;
    return 301 https://\$server_name\$request_uri;
}

server {
    listen 443 ssl http2;
    server_name $DOMAIN www.$DOMAIN;

    ssl_certificate /etc/letsencrypt/live/$DOMAIN/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMAIN/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-XSS-Protection "1; mode=block" always;

    root /var/www/agrishield;
    index index.html;

    location / {
        try_files \$uri \$uri/ /index.html;
        expires 1h;
    }

    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)\$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 90;
    }

    location /health {
        access_log off;
        proxy_pass http://localhost:8080/health;
    }
}
NGINXFILE

ln -sf /etc/nginx/sites-available/agrishield /etc/nginx/sites-enabled/agrishield
rm -f /etc/nginx/sites-enabled/default

nginx -t > /dev/null 2>&1
systemctl restart nginx

# Generate SSL certificate
echo "Generating SSL certificate for $DOMAIN..."
certbot certonly --standalone -d "$DOMAIN" -d "www.$DOMAIN" --non-interactive --agree-tos -m "deploy@$DOMAIN" --quiet

systemctl restart nginx
systemctl enable nginx > /dev/null 2>&1

echo "✓ Nginx and SSL configured"

# Copy React frontend
echo "Deploying React frontend..."
cd "$APP_HOME/agrishield/frontend"
sudo -u agrishield npm install -q
sudo -u agrishield npm run build -q
cp -r dist/* /var/www/agrishield/
chown -R www-data:www-data /var/www/agrishield

# Start Tomcat
echo "Starting services..."
systemctl enable tomcat-agrishield > /dev/null 2>&1
systemctl start tomcat-agrishield

# Wait for startup
sleep 5

echo ""
echo "========================================"
echo "✓ DEPLOYMENT COMPLETE!"
echo "========================================"
echo ""
echo "Test your deployment at:"
echo "  https://$DOMAIN"
echo ""
echo "Admin credentials (for testing):"
echo "  Email: admin@agrishield.tz"
echo "  Password: Admin#123"
echo "  TOTP: 123456 (using Google Authenticator)"
echo ""
echo "API endpoints:"
echo "  POST   https://$DOMAIN/api/auth/login"
echo "  POST   https://$DOMAIN/api/auth/social"
echo "  POST   https://$DOMAIN/api/auth/password/forgot"
echo "  POST   https://$DOMAIN/api/auth/password/reset"
echo "  GET    https://$DOMAIN/api/auth/me"
echo ""
echo "Monitoring:"
echo "  systemctl status tomcat-agrishield"
echo "  tail -f /var/log/nginx/error.log"
echo ""
echo "========================================"

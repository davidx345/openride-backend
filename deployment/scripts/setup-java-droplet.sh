#!/bin/bash

# ===================================
# OpenRIDE - Java Droplet Setup Script
# ===================================
# This script sets up a DigitalOcean Droplet for Java microservices
# Run this on your Java droplet after initial creation

set -e

echo "🚀 Setting up Java Droplet for OpenRIDE..."

# Update system
echo "📦 Updating system packages..."
sudo apt-get update
sudo apt-get upgrade -y

# Install Docker
echo "🐳 Installing Docker..."
sudo apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Start and enable Docker
sudo systemctl start docker
sudo systemctl enable docker

# Add current user to docker group
sudo usermod -aG docker $USER

# Install Docker Compose standalone
echo "🔧 Installing Docker Compose..."
sudo curl -L "https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Install Nginx
echo "🌐 Installing Nginx..."
sudo apt-get install -y nginx

# Install Certbot for Let's Encrypt
echo "🔒 Installing Certbot..."
sudo apt-get install -y certbot python3-certbot-nginx

# Configure firewall
echo "🔥 Configuring firewall..."
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable

# Create deployment directory
echo "📁 Creating deployment directory..."
mkdir -p ~/openride-backend
cd ~/openride-backend

# Install monitoring tools
echo "📊 Installing monitoring tools..."
sudo apt-get install -y htop

# Set up log rotation
echo "📝 Setting up log rotation..."
sudo tee /etc/logrotate.d/openride-java << EOF
/var/log/openride-java/*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    create 0644 root root
}
EOF

echo "✅ Java Droplet setup complete!"
echo ""
echo "Next steps:"
echo "1. Log out and log back in for Docker group changes to take effect"
echo "2. Clone your repository: git clone https://github.com/davidx345/openride-backend.git"
echo "3. Copy .env.example to .env and fill in your secrets"
echo "4. Run: ./deploy-java.sh"
echo ""

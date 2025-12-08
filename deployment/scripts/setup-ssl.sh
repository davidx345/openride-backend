#!/bin/bash

# ===================================
# OpenRIDE - SSL Certificate Setup
# ===================================
# Sets up Let's Encrypt SSL certificates using Certbot
# Run this after DNS is configured and pointing to your droplet

set -e

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <domain> <email>"
    echo "Example: $0 api-java.yourdomain.com admin@yourdomain.com"
    exit 1
fi

DOMAIN=$1
EMAIL=$2

echo "🔒 Setting up SSL certificate for $DOMAIN..."

# Stop Nginx temporarily
sudo systemctl stop nginx

# Obtain certificate
sudo certbot certonly --standalone \
    --non-interactive \
    --agree-tos \
    --email "$EMAIL" \
    -d "$DOMAIN"

# Start Nginx
sudo systemctl start nginx

# Test certificate renewal
sudo certbot renew --dry-run

echo "✅ SSL certificate installed successfully!"
echo ""
echo "Certificate location: /etc/letsencrypt/live/$DOMAIN/"
echo "Auto-renewal is configured via systemd timer"
echo ""
echo "Update your Nginx config to use this certificate, then reload:"
echo "  sudo nginx -t"
echo "  sudo systemctl reload nginx"
echo ""

#!/bin/bash

# MeshChat Server Deployment Script
# Reliable deployment script for VPS servers

set -e  # Exit on any error

# Configuration
SERVER_NAME="MeshChat Server"
VERSION="1.0.0"
DEPLOY_DIR="/opt/meshchat"
BACKUP_DIR="/opt/meshchat/backups"
LOG_FILE="/var/log/meshchat-deploy.log"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "$LOG_FILE"
    exit 1
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "$LOG_FILE"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$LOG_FILE"
}

# Check if running as root
check_root() {
    if [[ $EUID -eq 0 ]]; then
        error "This script should not be run as root for security reasons"
    fi
}

# Check system requirements
check_requirements() {
    log "Checking system requirements..."
    
    # Check if Docker is installed
    if ! command -v docker &> /dev/null; then
        error "Docker is not installed. Please install Docker first."
    fi
    
    # Check if Docker Compose is installed
    if ! command -v docker-compose &> /dev/null; then
        error "Docker Compose is not installed. Please install Docker Compose first."
    fi
    
    # Check available disk space (at least 2GB)
    available_space=$(df / | awk 'NR==2 {print $4}')
    if [[ $available_space -lt 2097152 ]]; then
        error "Insufficient disk space. At least 2GB required."
    fi
    
    # Check available memory (at least 1GB)
    available_memory=$(free -m | awk 'NR==2{printf "%.0f", $7}')
    if [[ $available_memory -lt 1024 ]]; then
        warning "Less than 1GB RAM available. Performance may be affected."
    fi
    
    success "System requirements check passed"
}

# Setup directories
setup_directories() {
    log "Setting up directories..."
    
    sudo mkdir -p "$DEPLOY_DIR"
    sudo mkdir -p "$BACKUP_DIR"
    sudo mkdir -p "$(dirname "$LOG_FILE")"
    
    # Set proper permissions
    sudo chown -R "$USER:$USER" "$DEPLOY_DIR"
    sudo chown -R "$USER:$USER" "$BACKUP_DIR"
    
    success "Directories setup completed"
}

# Backup existing deployment
backup_existing() {
    if [[ -d "$DEPLOY_DIR/meshchat-server" ]]; then
        log "Creating backup of existing deployment..."
        
        backup_name="meshchat-backup-$(date +%Y%m%d-%H%M%S)"
        cp -r "$DEPLOY_DIR/meshchat-server" "$BACKUP_DIR/$backup_name"
        
        # Keep only last 5 backups
        cd "$BACKUP_DIR"
        ls -t | tail -n +6 | xargs -r rm -rf
        
        success "Backup created: $backup_name"
    fi
}

# Download and extract server
deploy_server() {
    log "Deploying $SERVER_NAME v$VERSION..."
    
    cd "$DEPLOY_DIR"
    
    # Create deployment directory
    mkdir -p meshchat-server
    cd meshchat-server
    
    # Copy server files (assuming they're in current directory)
    if [[ -f "../server/build.gradle.kts" ]]; then
        cp -r ../server/* .
    else
        error "Server source files not found. Please ensure server directory exists."
    fi
    
    success "Server files deployed"
}

# Configure environment
configure_environment() {
    log "Configuring environment..."
    
    cd "$DEPLOY_DIR/meshchat-server"
    
    # Create environment file if it doesn't exist
    if [[ ! -f ".env" ]]; then
        cat > .env << EOF
# MeshChat Server Environment Configuration

# Server Configuration
MESHCHAT_HOST=0.0.0.0
MESHCHAT_PORT=8080
MESHCHAT_MAX_CONNECTIONS=1000

# Database Configuration
DB_DRIVER=org.postgresql.Driver
DB_URL=jdbc:postgresql://postgres:5432/meshchat
DB_USERNAME=meshchat
DB_PASSWORD=$(openssl rand -base64 32)

# Security
MESHCHAT_ENABLE_TLS=false

# Monitoring
METRICS_HOST=0.0.0.0
METRICS_PORT=9090
LOG_LEVEL=INFO

# JVM Options
JAVA_OPTS=-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
EOF
        success "Environment configuration created"
    else
        log "Using existing environment configuration"
    fi
}

# Setup SSL certificates (optional)
setup_ssl() {
    log "Setting up SSL certificates..."
    
    cd "$DEPLOY_DIR/meshchat-server"
    
    if [[ ! -d "ssl" ]]; then
        mkdir -p ssl
        
        # Generate self-signed certificate for development
        openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
            -keyout ssl/meshchat.key \
            -out ssl/meshchat.crt \
            -subj "/C=US/ST=State/L=City/O=Organization/CN=localhost"
        
        success "Self-signed SSL certificate generated"
    else
        log "SSL certificates already exist"
    fi
}

# Setup monitoring configuration
setup_monitoring() {
    log "Setting up monitoring configuration..."
    
    cd "$DEPLOY_DIR/meshchat-server"
    
    # Prometheus configuration
    cat > prometheus.yml << EOF
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'meshchat-server'
    static_configs:
      - targets: ['meshchat-server:9090']
    scrape_interval: 5s
    metrics_path: /metrics

  - job_name: 'postgres'
    static_configs:
      - targets: ['postgres:5432']

  - job_name: 'redis'
    static_configs:
      - targets: ['redis:6379']
EOF

    # Nginx configuration
    cat > nginx.conf << EOF
events {
    worker_connections 1024;
}

http {
    upstream meshchat_backend {
        server meshchat-server:8080;
    }

    server {
        listen 80;
        server_name _;

        location / {
            proxy_pass http://meshchat_backend;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto \$scheme;
        }

        location /health {
            proxy_pass http://meshchat-server:9090/health;
        }

        location /metrics {
            proxy_pass http://meshchat-server:9090/metrics;
        }
    }
}
EOF

    success "Monitoring configuration setup completed"
}

# Build and start services
start_services() {
    log "Building and starting services..."
    
    cd "$DEPLOY_DIR/meshchat-server"
    
    # Build the application
    ./gradlew shadowJar
    
    # Start services with Docker Compose
    docker-compose down --remove-orphans
    docker-compose build --no-cache
    docker-compose up -d
    
    # Wait for services to be healthy
    log "Waiting for services to be healthy..."
    sleep 30
    
    # Check health
    if curl -f http://localhost:9090/health > /dev/null 2>&1; then
        success "Services started successfully"
    else
        error "Health check failed. Check logs with: docker-compose logs"
    fi
}

# Setup systemd service (optional)
setup_systemd() {
    log "Setting up systemd service..."
    
    sudo tee /etc/systemd/system/meshchat.service > /dev/null << EOF
[Unit]
Description=MeshChat Server
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$DEPLOY_DIR/meshchat-server
ExecStart=/usr/local/bin/docker-compose up -d
ExecStop=/usr/local/bin/docker-compose down
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
EOF

    sudo systemctl daemon-reload
    sudo systemctl enable meshchat.service
    
    success "Systemd service setup completed"
}

# Setup firewall rules
setup_firewall() {
    log "Setting up firewall rules..."
    
    if command -v ufw &> /dev/null; then
        sudo ufw allow 8080/tcp comment "MeshChat Server"
        sudo ufw allow 9090/tcp comment "MeshChat Metrics"
        sudo ufw allow 80/tcp comment "HTTP"
        sudo ufw allow 443/tcp comment "HTTPS"
        success "UFW firewall rules added"
    elif command -v firewall-cmd &> /dev/null; then
        sudo firewall-cmd --permanent --add-port=8080/tcp
        sudo firewall-cmd --permanent --add-port=9090/tcp
        sudo firewall-cmd --permanent --add-port=80/tcp
        sudo firewall-cmd --permanent --add-port=443/tcp
        sudo firewall-cmd --reload
        success "Firewalld rules added"
    else
        warning "No firewall detected. Please manually configure firewall rules."
    fi
}

# Show deployment summary
show_summary() {
    log "Deployment completed successfully!"
    
    echo ""
    echo "=============================================="
    echo "  $SERVER_NAME v$VERSION Deployment Summary"
    echo "=============================================="
    echo ""
    echo "🌐 Server URL: http://$(hostname -I | awk '{print $1}'):8080"
    echo "📊 Metrics: http://$(hostname -I | awk '{print $1}'):9090/metrics"
    echo "❤️  Health Check: http://$(hostname -I | awk '{print $1}'):9090/health"
    echo "📈 Grafana: http://$(hostname -I | awk '{print $1}'):3000 (admin/admin_password_change_me)"
    echo ""
    echo "📁 Installation Directory: $DEPLOY_DIR/meshchat-server"
    echo "📋 Logs: docker-compose logs -f"
    echo "🔧 Configuration: $DEPLOY_DIR/meshchat-server/.env"
    echo ""
    echo "🚀 To manage the service:"
    echo "   Start: sudo systemctl start meshchat"
    echo "   Stop: sudo systemctl stop meshchat"
    echo "   Status: sudo systemctl status meshchat"
    echo ""
    echo "⚠️  Remember to:"
    echo "   1. Change default passwords in .env file"
    echo "   2. Configure SSL certificates for production"
    echo "   3. Set up regular backups"
    echo "   4. Monitor server resources"
    echo ""
}

# Main deployment function
main() {
    log "Starting $SERVER_NAME v$VERSION deployment..."
    
    check_root
    check_requirements
    setup_directories
    backup_existing
    deploy_server
    configure_environment
    setup_ssl
    setup_monitoring
    start_services
    setup_systemd
    setup_firewall
    show_summary
    
    success "Deployment completed successfully!"
}

# Handle script interruption
trap 'error "Deployment interrupted"' INT TERM

# Run main function
main "$@"
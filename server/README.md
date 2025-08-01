# 🌐 MeshChat Server

Надёжный высокопроизводительный TCP сервер для Android MeshChat клиентов, написанный на Kotlin с использованием современных технологий.

## ✨ Особенности

### 🔐 **Безопасность**
- **AES-256-GCM** шифрование сообщений
- **RSA-4096** обмен ключами
- **Цифровые подписи** для аутентификации
- **Perfect Forward Secrecy**
- **Rate limiting** защита от DDoS
- **BouncyCastle** криптография

### 🌐 **Сетевые возможности**
- **High-performance TCP** сервер на Netty
- **Множественные соединения** (до 1000+ клиентов)
- **Heartbeat механизм** для проверки соединений
- **Graceful shutdown** с proper cleanup
- **Connection pooling** и управление ресурсами

### 🗄️ **База данных**
- **PostgreSQL/H2** поддержка
- **Exposed ORM** для типобезопасности
- **Connection pooling** с HikariCP
- **Миграции** и schema management
- **Индексы** для производительности

### 📊 **Мониторинг**
- **Prometheus** метрики
- **Grafana** дашборды
- **Health checks** endpoints
- **Structured logging** с Logback
- **Performance monitoring**

### 🚀 **Deployment**
- **Docker** контейнеризация
- **Docker Compose** для полного стека
- **Автоматический deployment** скрипт
- **Systemd** integration
- **Nginx** reverse proxy

## 🏗️ Архитектура

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Android App   │    │   Android App    │    │   Android App   │
└─────────┬───────┘    └─────────┬────────┘    └─────────┬───────┘
          │                      │                       │
          └──────────────────────┼───────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │                         │
                    │    MeshChat Server      │
                    │                         │
                    │  ┌─────────────────┐    │
                    │  │  TCP Server     │    │
                    │  │  (Netty)        │    │
                    │  └─────────────────┘    │
                    │                         │
                    │  ┌─────────────────┐    │
                    │  │  Crypto Manager │    │
                    │  │  (AES+RSA)      │    │
                    │  └─────────────────┘    │
                    │                         │
                    │  ┌─────────────────┐    │
                    │  │  Rate Limiter   │    │
                    │  │  (DDoS Protection)   │
                    │  └─────────────────┘    │
                    └─────────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │      PostgreSQL         │
                    │    (Users & Messages)   │
                    └─────────────────────────┘
```

## 🚀 Быстрый старт

### Предварительные требования

- **Java 17+**
- **Docker & Docker Compose**
- **2GB+ RAM**
- **2GB+ свободного места**

### 1. Клонирование и сборка

```bash
# Перейти в директорию сервера
cd server

# Сделать deployment скрипт исполнимым
chmod +x deploy.sh

# Запустить автоматический deployment
./deploy.sh
```

### 2. Ручная установка

```bash
# Сборка проекта
./gradlew shadowJar

# Запуск с Docker Compose
docker-compose up -d

# Проверка здоровья
curl http://localhost:9090/health
```

### 3. Проверка работы

- **Сервер**: `http://your-server:8080`
- **Метрики**: `http://your-server:9090/metrics`
- **Health Check**: `http://your-server:9090/health`
- **Grafana**: `http://your-server:3000` (admin/admin_password_change_me)

## ⚙️ Конфигурация

### Environment Variables

```bash
# Сервер
MESHCHAT_HOST=0.0.0.0
MESHCHAT_PORT=8080
MESHCHAT_MAX_CONNECTIONS=1000

# База данных
DB_DRIVER=org.postgresql.Driver
DB_URL=jdbc:postgresql://postgres:5432/meshchat
DB_USERNAME=meshchat
DB_PASSWORD=your_secure_password

# Безопасность
MESHCHAT_ENABLE_TLS=false

# Мониторинг
METRICS_HOST=0.0.0.0
METRICS_PORT=9090
LOG_LEVEL=INFO
```

### application.conf

```hocon
server {
  host = "0.0.0.0"
  port = 8080
  maxConnections = 1000
  connectionTimeout = 30s
  heartbeatInterval = 30s
  bufferSize = 8192
  workerThreads = 8
}

database {
  driver = "org.postgresql.Driver"
  url = "jdbc:postgresql://localhost:5432/meshchat"
  username = "meshchat"
  password = "password"
  maxPoolSize = 20
  connectionTimeout = 30s
  maxLifetime = 30m
}

security {
  rsaKeySize = 4096
  aesKeySize = 256
  sessionTimeout = 24h
  maxFailedAttempts = 5
  rateLimitPerMinute = 60
}

monitoring {
  host = "0.0.0.0"
  port = 9090
  enableMetrics = true
  metricsPath = "/metrics"
  healthCheckPath = "/health"
  logLevel = "INFO"
}
```

## 🔧 API Endpoints

### Health & Monitoring

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Server health status |
| `/metrics` | GET | Prometheus metrics |
| `/` | GET | Server info page |

### TCP Protocol

Сервер использует TCP протокол с JSON сообщениями:

```json
{
  "type": "HANDSHAKE",
  "senderId": "client-id",
  "data": "{\"userId\":\"user123\",\"username\":\"Alice\",\"publicKey\":\"...\"}"
}
```

#### Message Types:
- `HANDSHAKE` - Инициализация соединения
- `HANDSHAKE_RESPONSE` - Ответ сервера
- `ENCRYPTED_MESSAGE` - Зашифрованное сообщение
- `USER_LIST` - Список пользователей
- `HEARTBEAT` - Проверка соединения
- `ERROR` - Сообщение об ошибке
- `DISCONNECT` - Отключение

## 📊 Мониторинг

### Prometheus Metrics

- `meshchat_connections_total` - Общее количество соединений
- `meshchat_current_connections` - Текущие соединения
- `meshchat_messages_total` - Общее количество сообщений
- `meshchat_bytes_transferred_total` - Переданные байты
- `meshchat_memory_usage_bytes` - Использование памяти

### Логирование

Логи разделены по категориям:

- `logs/meshchat-server.log` - Основные логи
- `logs/meshchat-security.log` - События безопасности
- `logs/meshchat-network.log` - Сетевые события

### Grafana Dashboards

Включены готовые дашборды для мониторинга:

- **Server Overview** - Общая статистика
- **Network Activity** - Сетевая активность
- **Security Events** - События безопасности
- **Performance** - Производительность

## 🔒 Безопасность

### Криптография

- **RSA-4096** для обмена ключами
- **AES-256-GCM** для шифрования сообщений
- **SHA-256** цифровые подписи
- **Perfect Forward Secrecy** поддержка

### Rate Limiting

```kotlin
// 60 запросов в минуту по умолчанию
val rateLimiter = RateLimiter(
    maxRequestsPerWindow = 60,
    windowSizeMs = 60_000
)
```

### DDoS Protection

- **Connection limits** (1000 по умолчанию)
- **Rate limiting** по IP и пользователю
- **Automatic blocking** при превышении лимитов
- **Graceful degradation** при высокой нагрузке

## 🐳 Docker

### Dockerfile

Многоэтапная сборка для оптимизации размера:

```dockerfile
FROM openjdk:17-jdk-slim as builder
# Build stage...

FROM openjdk:17-jre-slim
# Runtime stage...
```

### Docker Compose

Полный стек с мониторингом:

```yaml
services:
  meshchat-server:
    build: .
    ports:
      - "8080:8080"
      - "9090:9090"
  
  postgres:
    image: postgres:15-alpine
    
  prometheus:
    image: prom/prometheus:latest
    
  grafana:
    image: grafana/grafana:latest
```

## 🚀 Production Deployment

### VPS Requirements

- **OS**: Ubuntu 20.04+ / CentOS 8+
- **RAM**: 2GB+ (рекомендуется 4GB+)
- **CPU**: 2 cores+ (рекомендуется 4 cores+)
- **Storage**: 10GB+ SSD
- **Network**: 100Mbps+

### SSL/TLS Setup

```bash
# Генерация Let's Encrypt сертификата
sudo certbot --nginx -d your-domain.com

# Обновление конфигурации
MESHCHAT_ENABLE_TLS=true
```

### Systemd Service

```ini
[Unit]
Description=MeshChat Server
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/meshchat/meshchat-server
ExecStart=/usr/local/bin/docker-compose up -d
ExecStop=/usr/local/bin/docker-compose down

[Install]
WantedBy=multi-user.target
```

### Firewall Configuration

```bash
# UFW
sudo ufw allow 8080/tcp  # MeshChat
sudo ufw allow 9090/tcp  # Metrics
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS

# Firewalld
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

## 🔧 Maintenance

### Backup

```bash
# Backup database
docker exec meshchat-postgres pg_dump -U meshchat meshchat > backup.sql

# Backup configuration
tar -czf config-backup.tar.gz .env docker-compose.yml nginx.conf
```

### Updates

```bash
# Pull latest changes
git pull origin main

# Rebuild and restart
docker-compose down
docker-compose build --no-cache
docker-compose up -d
```

### Monitoring Commands

```bash
# Проверка статуса
sudo systemctl status meshchat

# Просмотр логов
docker-compose logs -f meshchat-server

# Проверка ресурсов
docker stats

# Проверка соединений
netstat -tulpn | grep 8080
```

## 🐛 Troubleshooting

### Общие проблемы

1. **Сервер не запускается**
   ```bash
   # Проверить логи
   docker-compose logs meshchat-server
   
   # Проверить порты
   sudo netstat -tulpn | grep 8080
   ```

2. **Проблемы с базой данных**
   ```bash
   # Проверить PostgreSQL
   docker-compose logs postgres
   
   # Подключиться к БД
   docker exec -it meshchat-postgres psql -U meshchat
   ```

3. **Высокое использование памяти**
   ```bash
   # Настроить JVM
   JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC"
   ```

### Performance Tuning

```bash
# Увеличить file descriptors
echo "* soft nofile 65536" >> /etc/security/limits.conf
echo "* hard nofile 65536" >> /etc/security/limits.conf

# Настройка TCP
echo "net.core.somaxconn = 1024" >> /etc/sysctl.conf
sysctl -p
```

## 📈 Scaling

### Horizontal Scaling

- **Load Balancer** (Nginx/HAProxy)
- **Multiple server instances**
- **Shared database**
- **Redis for session management**

### Vertical Scaling

- **Увеличить RAM** для JVM heap
- **Больше CPU cores** для worker threads
- **SSD storage** для базы данных
- **Faster network** для высокой нагрузки

## 🤝 Contributing

1. Fork проект
2. Создать feature branch (`git checkout -b feature/amazing-feature`)
3. Commit изменения (`git commit -m 'Add amazing feature'`)
4. Push в branch (`git push origin feature/amazing-feature`)
5. Открыть Pull Request

## 📄 License

Этот проект лицензирован под MIT License - см. [LICENSE](LICENSE) файл.

## 🙏 Acknowledgments

- **Netty** - Высокопроизводительный network framework
- **Exposed** - Kotlin SQL framework
- **BouncyCastle** - Криптографическая библиотека
- **Micrometer** - Metrics collection
- **Docker** - Контейнеризация

---

## 📞 Support

Если у вас есть вопросы или проблемы:

1. Проверьте [Issues](https://github.com/your-repo/issues)
2. Создайте новый Issue с подробным описанием
3. Приложите логи и конфигурацию

**Версия**: 1.0.0  
**Kotlin**: 1.9.22  
**Java**: 17+  
**Совместимость**: Android API 24+
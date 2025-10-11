# Redis Configuration for BantuOps Backend

## Overview

This document describes the Redis configuration implemented for task 1.4 "Configurer Redis pour cache et sessions". The implementation provides comprehensive caching strategies, distributed session management, and performance optimization for the BantuOps backend application.

## Features Implemented

### 1. RedisCacheManager avec TTL appropriés ✅

**Configuration**: `RedisConfig.java`

- **Default TTL**: 1 hour for general caches
- **Specific TTL configurations**:
  - `employees`: 2 hours (relatively stable data)
  - `payroll-calculations`: 30 minutes (frequently changing)
  - `tax-rates`: 24 hours (rarely changing)
  - `attendance-rules`: 12 hours (business rules)
  - `user-permissions`: 1 hour (security-sensitive)
  - `financial-reports`: 15 minutes (dashboard data)
  - `invoices`: 1 hour (business data)
  - `system-config`: 6 hours (configuration data)
  - `frequent-calculations`: 10 minutes (real-time calculations)
  - `session-metadata`: 25 hours (outlives sessions)
  - `business-rules`: 4 hours (business logic)
  - `audit-cache`: 2 hours (audit data)

**Features**:
- Transaction-aware cache manager
- Statistics enabled for monitoring
- Null value caching disabled for performance
- JSON serialization with Jackson

### 2. Stratégie de cache pour les calculs fréquents ✅

**Service**: `CachedCalculationService.java`

**Cached Operations**:
- Tax rate calculations for Senegalese brackets
- Social contribution calculations (IPRES, CSS, FNR)
- Dashboard metrics with short TTL
- Business rules (tax brackets, VAT rates, overtime rules)
- Monthly summaries for employees
- Attendance rules and policies

**Cache Strategies**:
- **Frequent calculations**: 10-minute TTL for real-time data
- **Business rules**: 4-hour TTL for stable business logic
- **Tax calculations**: Cached by salary and period
- **Employee data**: 2-hour TTL with eviction on updates

**Cache Warm-up**:
- Automatic warm-up on application startup
- Pre-loads common salary ranges and tax calculations
- Pre-loads business rules and attendance policies

### 3. Spring Session avec Redis ✅

**Configuration**: `RedisConfig.java` + `application.properties`

**Features**:
- Redis-backed session storage
- 1-hour session timeout (configurable)
- Automatic session cleanup every 5 minutes
- Session namespace: `bantuops:session`
- JSON serialization for session data

**Session Management**:
- Session tracking with metadata (user agent, IP address)
- Session invalidation across all nodes
- Session timeout management
- Audit trail for session events

### 4. Gestion des sessions distribuées ✅

**Service**: `DistributedSessionService.java`

**Features**:
- Cross-node session registry
- Distributed locking for session operations
- Session-to-node mapping
- User-to-session mapping
- Orphaned session cleanup
- Session event broadcasting

**Distributed Operations**:
- Register/unregister sessions across nodes
- Query sessions by user across all nodes
- Find which node hosts a specific session
- Invalidate user sessions across all nodes
- Cleanup orphaned sessions automatically

**Node Management**:
- Configurable node ID via `bantuops.node.id`
- Automatic node identification
- Session event broadcasting for coordination

## Services Overview

### Core Services

1. **CachedCalculationService**
   - Handles all cached business calculations
   - Implements cache eviction strategies
   - Provides cache warm-up functionality

2. **DistributedSessionService**
   - Manages distributed session registry
   - Handles cross-node session coordination
   - Implements distributed locking

3. **SessionManagementService**
   - Enhanced with distributed session support
   - Session metadata storage and retrieval
   - Integration with distributed session service

4. **CacheEvictionService**
   - Enhanced with new cache types
   - Handles cache invalidation events
   - Provides cache statistics logging

5. **RedisMaintenanceService**
   - Scheduled maintenance tasks
   - Session cleanup and health monitoring
   - Performance statistics logging

6. **RedisHealthService**
   - Redis connectivity monitoring
   - Performance testing
   - Health status reporting

## Configuration Files

### application.properties
```properties
# Redis Configuration
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD:}
spring.data.redis.timeout=2000ms
spring.data.redis.lettuce.pool.max-active=8
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=0

# Session Configuration
spring.session.store-type=redis
spring.session.redis.namespace=bantuops:session
spring.session.redis.cleanup-cron=0 */5 * * * *
spring.session.timeout=3600s

# Cache Configuration
spring.cache.type=redis
spring.cache.redis.time-to-live=3600000
spring.cache.redis.cache-null-values=false
spring.cache.redis.enable-statistics=true

# Node Configuration
bantuops.node.id=${NODE_ID:node-${random.int(1000,9999)}}
```

## Scheduled Tasks

### RedisMaintenanceService
- **Session cleanup**: Every 5 minutes
- **Orphaned session cleanup**: Every 15 minutes
- **Cache statistics**: Every hour
- **Session statistics**: Every 30 minutes
- **Redis health check**: Every hour

## Performance Optimizations

1. **Connection Pooling**: Configured Lettuce connection pool
2. **Serialization**: Optimized Jackson JSON serialization
3. **TTL Strategy**: Appropriate TTL for different data types
4. **Cache Statistics**: Enabled for monitoring and optimization
5. **Async Operations**: Scheduled tasks run asynchronously
6. **Distributed Locking**: Prevents race conditions in distributed environment

## Monitoring and Health Checks

### Health Endpoints
- Redis connectivity check
- Performance testing
- Cache statistics
- Session statistics
- Distributed session status

### Logging
- Debug logging for cache operations
- Info logging for maintenance tasks
- Error logging for failures
- Performance metrics logging

## Security Considerations

1. **Session Security**: Secure session storage with Redis
2. **Distributed Locking**: Prevents concurrent modification issues
3. **Session Invalidation**: Immediate invalidation across all nodes
4. **Audit Trail**: Complete session event logging
5. **Timeout Management**: Automatic session expiration

## Testing

### RedisConfigTest
- Verifies cache manager configuration
- Tests cache operations
- Validates distributed session management
- Confirms session tracking functionality

## Usage Examples

### Caching Business Calculations
```java
@Autowired
private CachedCalculationService calculationService;

// Tax calculation (cached for 24 hours)
BigDecimal taxRate = calculationService.calculateTaxRate(salary, period);

// Business rules (cached for 4 hours)
Map<String, BigDecimal> vatRates = calculationService.getSenegalVATRates();
```

### Distributed Session Management
```java
@Autowired
private DistributedSessionService distributedSessionService;

// Register session across nodes
distributedSessionService.registerDistributedSession(sessionId, userId, nodeId);

// Check session across all nodes
boolean exists = distributedSessionService.isSessionRegistered(sessionId);

// Find session node
String nodeId = distributedSessionService.getSessionNode(sessionId);
```

### Session Tracking with Metadata
```java
@Autowired
private SessionManagementService sessionService;

// Track session with metadata
sessionService.trackUserSession(userId, sessionId, userAgent, ipAddress);

// Get session statistics
Map<String, Object> stats = sessionService.getDistributedSessionStatistics();
```

## Requirements Compliance

✅ **Exigence 6.1**: Performance optimization with appropriate caching strategies
✅ **Exigence 6.2**: Efficient cache management with TTL configurations
✅ **Exigence 6.3**: Distributed session management for scalability
✅ **Exigence 6.4**: Monitoring and health checks for Redis operations

## Deployment Notes

1. **Redis Server**: Ensure Redis server is running and accessible
2. **Environment Variables**: Configure Redis connection parameters
3. **Node ID**: Set unique node ID for each application instance
4. **Monitoring**: Enable Redis monitoring for production environments
5. **Backup**: Configure Redis persistence for session data

This implementation provides a robust, scalable, and secure Redis configuration that meets all the requirements for task 1.4.
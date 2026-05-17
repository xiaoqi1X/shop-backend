-- Phase 1 seckill schema and seed data for database hm-trade.
-- Seed item_id 317578 exists in the local hm-item.item table used for integration tests.

CREATE TABLE IF NOT EXISTS seckill_activity (
  id BIGINT NOT NULL COMMENT 'seckill activity id',
  item_id BIGINT NOT NULL COMMENT 'item id',
  seckill_price INT NOT NULL COMMENT 'seckill price in cents',
  start_time DATETIME NOT NULL COMMENT 'activity start time',
  end_time DATETIME NOT NULL COMMENT 'activity end time',
  limit_count INT NOT NULL DEFAULT 1 COMMENT 'purchase limit per user',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0-disabled, 1-enabled, 2-ended',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  KEY idx_item_id (item_id),
  KEY idx_status_time (status, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='seckill activity';

CREATE TABLE IF NOT EXISTS seckill_stock (
  id BIGINT NOT NULL COMMENT 'stock record id',
  seckill_id BIGINT NOT NULL COMMENT 'seckill activity id',
  total_stock INT NOT NULL COMMENT 'total stock',
  available_stock INT NOT NULL COMMENT 'available stock',
  sold_count INT NOT NULL DEFAULT 0 COMMENT 'sold count',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_seckill_id (seckill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='seckill stock';

CREATE TABLE IF NOT EXISTS seckill_order (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'seckill order id',
  request_id VARCHAR(64) DEFAULT NULL COMMENT 'request id for MQ tracing',
  seckill_id BIGINT NOT NULL COMMENT 'seckill activity id',
  item_id BIGINT NOT NULL COMMENT 'item id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  num INT NOT NULL COMMENT 'purchase quantity',
  seckill_price INT NOT NULL COMMENT 'seckill unit price in cents',
  total_fee INT NOT NULL COMMENT 'total fee in cents',
  status VARCHAR(32) NOT NULL COMMENT 'order status',
  result_code VARCHAR(32) NOT NULL COMMENT 'result code',
  failure_reason VARCHAR(255) DEFAULT NULL COMMENT 'failure reason',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_seckill (user_id, seckill_id),
  KEY idx_seckill_status (seckill_id, status),
  KEY idx_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='seckill order';

CREATE TABLE IF NOT EXISTS seckill_order_outbox (
  id BIGINT NOT NULL COMMENT 'outbox record id',
  request_id VARCHAR(64) NOT NULL COMMENT 'request id for idempotent MQ send',
  order_id BIGINT NOT NULL COMMENT 'seckill order id to send',
  seckill_id BIGINT NOT NULL COMMENT 'seckill activity id',
  item_id BIGINT NOT NULL COMMENT 'item id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  num INT NOT NULL COMMENT 'purchase quantity',
  seckill_price INT NOT NULL COMMENT 'seckill unit price in cents',
  total_fee INT NOT NULL COMMENT 'total fee in cents',
  status VARCHAR(32) NOT NULL COMMENT 'NEW, SENT, SEND_FAILED, FINALIZED, FAILED',
  retry_count INT NOT NULL DEFAULT 0 COMMENT 'send retry count',
  max_retry_count INT NOT NULL DEFAULT 5 COMMENT 'maximum send retry count',
  next_retry_time DATETIME DEFAULT NULL COMMENT 'next retry time',
  last_error VARCHAR(512) DEFAULT NULL COMMENT 'last send or finalization error',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_request_id (request_id),
  UNIQUE KEY uk_order_id (order_id),
  KEY idx_status_retry_time (status, next_retry_time),
  KEY idx_user_seckill (user_id, seckill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='seckill order outbox';

INSERT INTO seckill_activity (id, item_id, seckill_price, start_time, end_time, limit_count, status)
VALUES
  (1, 317578, 9900, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 1 DAY), 1, 1),
  (2, 317578, 9900, DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), 1, 1),
  (3, 317578, 9900, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 1, 1),
  (4, 317578, 9900, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 1 DAY), 1, 1)
ON DUPLICATE KEY UPDATE
  item_id = VALUES(item_id),
  seckill_price = VALUES(seckill_price),
  start_time = VALUES(start_time),
  end_time = VALUES(end_time),
  limit_count = VALUES(limit_count),
  status = VALUES(status);

INSERT INTO seckill_stock (id, seckill_id, total_stock, available_stock, sold_count)
VALUES
  (1, 1, 10, 10, 0),
  (2, 2, 10, 10, 0),
  (3, 3, 10, 10, 0),
  (4, 4, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  total_stock = VALUES(total_stock),
  available_stock = VALUES(available_stock),
  sold_count = VALUES(sold_count);

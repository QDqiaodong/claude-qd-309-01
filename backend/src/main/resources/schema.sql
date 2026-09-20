-- 洗车房 · 工位与洗车单
SET NAMES utf8mb4;

DROP TABLE IF EXISTS card_flow;
DROP TABLE IF EXISTS rework_supply;
DROP TABLE IF EXISTS rework;
DROP TABLE IF EXISTS wash_order;
DROP TABLE IF EXISTS supply;
DROP TABLE IF EXISTS member_card;
DROP TABLE IF EXISTS bay;

CREATE TABLE bay (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  bay_code   VARCHAR(20) NOT NULL,
  bay_name   VARCHAR(60) NOT NULL,
  seat_count INT         NULL,
  bay_state  VARCHAR(12) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_bay_code (bay_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wash_order (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  order_no     VARCHAR(20) NOT NULL,
  plate_no     VARCHAR(16) NOT NULL,
  bay_id       BIGINT      NULL,
  service_type VARCHAR(24) NULL,
  price        INT         NULL,
  order_date   DATE        NULL,
  wash_state   VARCHAR(12) NOT NULL,
  pay_state    VARCHAR(8)  NOT NULL DEFAULT '未支付',
  pay_flow_id  BIGINT      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_order_bay (bay_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 回炉台：挂在已完成洗车单上的返工单，状态「待回炉 → 回炉中 → 已验收」
CREATE TABLE rework (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  rework_no     VARCHAR(20)  NOT NULL,
  order_id      BIGINT       NOT NULL,
  bay_id        BIGINT       NOT NULL,
  reason        VARCHAR(120) NULL,
  rework_state  VARCHAR(12)  NOT NULL,
  created_date  DATE         NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rework_no (rework_no),
  KEY idx_rework_order (order_id),
  KEY idx_rework_bay (bay_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 回炉二次领料：挂回炉的同时按行出库（泡沫、毛巾）；回炉作废逐行退架（returned=1），验收通过不退
CREATE TABLE rework_supply (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  rework_id   BIGINT      NOT NULL,
  supply_id   BIGINT      NOT NULL,
  quantity    INT         NOT NULL,
  returned    TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_rework_supply_rework (rework_id),
  KEY idx_rework_supply_supply (supply_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE supply (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  supply_code  VARCHAR(20) NOT NULL,
  supply_name  VARCHAR(60) NOT NULL,
  unit_text    VARCHAR(12) NULL,
  stock        INT         NOT NULL DEFAULT 0,
  warn_line    INT         NULL,
  supply_state VARCHAR(12) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_supply_code (supply_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE member_card (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  card_no     VARCHAR(20) NOT NULL,
  holder_name VARCHAR(40) NOT NULL,
  phone       VARCHAR(11) NULL,
  balance     INT         NOT NULL DEFAULT 0,
  card_level  VARCHAR(16) NULL,
  card_state  VARCHAR(12) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_no (card_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 卡流水：每动一次卡余额落一行，钉卡、钉洗车单、钉这笔之后的卡余额；
-- 退款行用 ref_flow_id 指回原来那笔扣款。晚班对账按这张表从卡流水找单。
CREATE TABLE card_flow (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  flow_no       VARCHAR(24) NOT NULL,
  card_id       BIGINT      NOT NULL,
  order_id      BIGINT      NOT NULL,
  amount        INT         NOT NULL,
  flow_type     VARCHAR(8)  NOT NULL,
  balance_after INT         NOT NULL,
  ref_flow_id   BIGINT      NULL,
  created_at    DATETIME    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_no (flow_no),
  KEY idx_flow_card (card_id),
  KEY idx_flow_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO bay (bay_code, bay_name, seat_count, bay_state) VALUES
('B-01', '标准洗车位 1', 1, '占用'),
('B-02', '标准洗车位 2', 1, '空闲'),
('B-03', '精洗位 A', 1, '空闲'),
('B-04', '内饰清洁位', 1, '停用');

INSERT INTO wash_order (order_no, plate_no, bay_id, service_type, price, order_date, wash_state, pay_state, pay_flow_id) VALUES
('WO-01', '京A12345', 1, '标准洗车', 30, '2026-09-19', '清洗中', '未支付', NULL),
('WO-02', '京N88888', 2, '精洗打蜡', 120, '2026-09-19', '待洗', '未支付', NULL),
('WO-03', '京Q66666', 3, '内饰清洁', 180, '2026-09-19', '待洗', '未支付', NULL),
('WO-04', '京B23456', 1, '标准洗车', 30, '2026-09-18', '已完成', '已支付', 1),
('WO-05', '京C34567', 2, '标准洗车', 30, '2026-09-18', '已完成', '未支付', NULL);

-- 一张未结回炉：原单 WO-05（已完成，原工位 2 号），车还没进场，待回炉不占座
INSERT INTO rework (rework_no, order_id, bay_id, reason, rework_state, created_date) VALUES
('RW-01', 5, 2, '洗完发现后门还有水痕，客人要求返工', '待回炉', '2026-09-19');

INSERT INTO supply (supply_code, supply_name, unit_text, stock, warn_line, supply_state) VALUES
('SP-01', '洗车液', '桶', 18, 5, '正常'),
('SP-02', '超细纤维毛巾', '条', 40, 50, '不足'),
('SP-03', '水晶蜡', '瓶', 3, 5, '不足'),
('SP-04', '轮胎光亮剂', '瓶', 12, 4, '正常'),
('SP-05', '玻璃清洁剂', '瓶', 0, 6, '已用完'),
('SP-06', '洗车泡沫', '瓶', 15, 5, '正常');

INSERT INTO member_card (card_no, holder_name, phone, balance, card_level, card_state) VALUES
('MC-01', '张伟', '13800000001', 530, '金卡', '正常'),
('MC-02', '李娜', '13800000002', 120, '银卡', '正常'),
('MC-03', '王强', '13800000003', 0, '普通卡', '已停卡'),
('MC-04', '赵敏', '13800000004', 300, '银卡', '正常'),
('MC-05', '刘洋', '13800000005', 88, '普通卡', '正常');

-- WO-04 刷 MC-01 结的 30 元：流水、单上的 pay_flow_id、卡余额（560 - 30 = 530）三头对得上
INSERT INTO card_flow (flow_no, card_id, order_id, amount, flow_type, balance_after, ref_flow_id, created_at) VALUES
('CF-01', 1, 4, 30, '扣款', 530, NULL, '2026-09-18 10:30:00');

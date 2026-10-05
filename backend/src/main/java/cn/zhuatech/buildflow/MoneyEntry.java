// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.buildflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 不可覆盖的线下收付退款及关联冲正流水。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "money_entry")
public class MoneyEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "project_id", nullable = false)
  public Long projectId;

  @Column(name = "bill_id", nullable = false)
  public Long billId;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount;

  @Column(name = "original_id")
  public Long originalId;

  @Column(name = "reference", nullable = false, length = 120)
  public String reference;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "request_key", nullable = false, length = 80)
  public String requestKey;

  @Column(name = "fingerprint", nullable = false, length = 64)
  public String fingerprint;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}

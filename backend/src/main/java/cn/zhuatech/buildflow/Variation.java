// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.buildflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 增减工程量与成本预算变更审批。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "variation")
public class Variation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "project_id", nullable = false)
  public Long projectId;

  @Column(name = "work_id", nullable = false)
  public Long workId;

  @Column(name = "quantity_delta", nullable = false, precision = 18, scale = 4)
  public BigDecimal quantityDelta;

  @Column(name = "budget_delta", nullable = false, precision = 18, scale = 2)
  public BigDecimal budgetDelta;

  @Column(name = "reason", nullable = false, length = 1000)
  public String reason;

  @Column(name = "reference", nullable = false, length = 120)
  public String reference;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "creator_id", nullable = false)
  public Long creatorId;

  @Column(name = "reviewer_id")
  public Long reviewerId;

  @Column(name = "review_note", nullable = false, length = 1000)
  public String reviewNote;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}

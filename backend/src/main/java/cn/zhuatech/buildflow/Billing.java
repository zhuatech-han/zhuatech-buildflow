// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.buildflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 基于已验收工程量的分期客户及分包结算单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "billing")
public class Billing {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "project_id", nullable = false)
  public Long projectId;

  @Column(name = "work_id", nullable = false)
  public Long workId;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

  @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
  public BigDecimal quantity;

  @Column(name = "gross", nullable = false, precision = 18, scale = 2)
  public BigDecimal gross;

  @Column(name = "retention", nullable = false, precision = 18, scale = 2)
  public BigDecimal retention;

  @Column(name = "released", nullable = false)
  public boolean released;

  @Column(name = "due_date", nullable = false)
  public LocalDate dueDate;

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

  @Column(name = "release_reference", length = 120)
  public String releaseReference;

  @Column(name = "release_actor_id")
  public Long releaseActorId;

  @Column(name = "released_at")
  public Instant releasedAt;
}

// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.buildflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 现场工程量申报与独立验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "progress_claim")
public class ProgressClaim {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "project_id", nullable = false)
  public Long projectId;

  @Column(name = "work_id", nullable = false)
  public Long workId;

  @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
  public BigDecimal quantity;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "work_date", nullable = false)
  public LocalDate workDate;

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

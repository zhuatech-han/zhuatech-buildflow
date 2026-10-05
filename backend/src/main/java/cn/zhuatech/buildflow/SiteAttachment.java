// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.buildflow;

import jakarta.persistence.*;
import java.time.*;

/** 工程量申报的受控图片附件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "site_attachment")
public class SiteAttachment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "project_id", nullable = false)
  public Long projectId;

  @Column(name = "claim_id", nullable = false)
  public Long claimId;

  @Column(name = "filename", nullable = false, length = 120)
  public String filename;

  @Column(name = "media_type", nullable = false, length = 30)
  public String mediaType;

  @Column(name = "creator_id", nullable = false)
  public Long creatorId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Lob
  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "content", nullable = false, columnDefinition = "MEDIUMBLOB", length = 2097152)
  public byte[] content;
}

// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.buildflow;

import jakarta.persistence.*;
import java.time.*;

/** 工程合同、项目成员与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "build_project")
public class Project {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "customer", nullable = false, length = 120)
  public String customer;

  @Column(name = "site", nullable = false, length = 300)
  public String site;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "manager_id", nullable = false)
  public Long managerId;

  @Column(name = "client_id")
  public Long clientId;

  @Column(name = "start_date", nullable = false)
  public LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  public LocalDate endDate;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "revision", nullable = false)
  public long revision;
}

// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.buildflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 合同工程量、冻结单价与分包承诺。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "work_item")
public class WorkItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "project_id", nullable = false)
  public Long projectId;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "unit", nullable = false, length = 30)
  public String unit;

  @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
  public BigDecimal quantity;

  @Column(name = "price", nullable = false, precision = 18, scale = 2)
  public BigDecimal price;

  @Column(name = "sub_price", nullable = false, precision = 18, scale = 2)
  public BigDecimal subPrice;

  @Column(name = "vendor", nullable = false, length = 120)
  public String vendor;

  @Column(name = "cost_budget", nullable = false, precision = 18, scale = 2)
  public BigDecimal costBudget;

  @Column(name = "due_date", nullable = false)
  public LocalDate dueDate;
}

// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.buildflow;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 工程合同至验收及收付款的事务边界；同项目行锁防止并发超结算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class BuildService {
  final Store db;
  final AccessService access;
  final Clock clock;

  public BuildService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 客户仅查看绑定项目，现场仅查看明确成员项目，内部岗位按部门范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean visible(Project p) {
    var a = access.current();
    var role = access.role();
    if (role.scope.equals("ASSIGNED") && role.permissions.contains("client"))
      return Objects.equals(p.clientId, a.id);
    if (role.scope.equals("ASSIGNED"))
      return db.query(
                  ProjectMember.class,
                  "from ProjectMember where projectId=?1 and accountId=?2",
                  p.id,
                  a.id)
              .size()
          > 0;
    return access.visible(p.departmentId);
  }

  /** 读取有权限的项目，写操作以项目锁串行化。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Project project(Long id, boolean locked) {
    access.require("project.read");
    var p = locked ? db.lock(Project.class, id) : db.get(Project.class, id);
    if (!visible(p)) throw new Problem(403, "OUT_OF_SCOPE");
    return p;
  }

  private boolean isClient() {
    var r = access.role();
    return r.scope.equals("ASSIGNED") && r.permissions.contains("client");
  }

  private void internal(Project p, String permission) {
    access.require(permission);
    if (isClient()) throw new Problem(403, "FORBIDDEN");
    if (!visible(p)) throw new Problem(403, "OUT_OF_SCOPE");
  }

  private void active(Project p) {
    if (!p.status.equals("ACTIVE")) throw new Problem(409, "PROJECT_NOT_ACTIVE");
  }

  private String text(Map<String, Object> v, String key, int max) {
    return AdminService.text((String) v.get(key), max);
  }

  private String note(Map<String, Object> v, String key) {
    var s = (String) v.getOrDefault(key, "");
    if (s == null || s.length() > 1000) throw new Problem(400, "INVALID_INPUT");
    return s.trim();
  }

  private Long id(Map<String, Object> v, String key) {
    try {
      return Long.valueOf(v.get(key).toString());
    } catch (Exception e) {
      throw new Problem(400, "INVALID_INPUT");
    }
  }

  private BigDecimal number(Map<String, Object> v, String key, int scale, boolean positive) {
    return BuildPolicy.decimal(v.get(key), scale, positive);
  }

  private LocalDate date(Map<String, Object> v, String key) {
    return LocalDate.parse(text(v, key, 10));
  }

  private Account account(Long id, String permission, Long department) {
    var a = db.get(Account.class, id);
    var r = db.get(AccessRole.class, a.roleId);
    if (!a.enabled
        || !r.permissions.contains(permission)
        || !Objects.equals(a.departmentId, department)) throw new Problem(400, "INVALID_ASSIGNEE");
    return a;
  }

  private void revision(Project p, Map<String, Object> v) {
    if (v.get("revision") == null || Long.parseLong(v.get("revision").toString()) != p.revision)
      throw new Problem(409, "STALE_REVISION");
  }

  private void changed(Project p, String action, Object id) {
    p.revision++;
    access.audit(action, id, p.departmentId);
  }

  private WorkItem work(Project p, Long id) {
    var w = db.get(WorkItem.class, id);
    if (!Objects.equals(w.projectId, p.id)) throw new Problem(400, "WRONG_PROJECT");
    return w;
  }

  /** 列出项目，用角色过滤后返回；列表可由客户端分页筛选。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<?> projects() {
    access.require("project.read");
    return db.all(Project.class).stream()
        .filter(this::visible)
        .map(
            p ->
                (access.role().scope.equals("ASSIGNED") || isClient())
                    ? Map.<String, Object>of("project", p)
                    : summary(p))
        .toList();
  }

  /** 创建草稿合同，验证项目经理、客户绑定与时间区间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Project create(Map<String, Object> v) {
    access.require("project.write");
    if (access.role().scope.equals("ASSIGNED")) throw new Problem(403, "FORBIDDEN");
    var p = new Project();
    p.code = text(v, "code", 60);
    p.name = text(v, "name", 120);
    p.customer = text(v, "customer", 120);
    p.site = text(v, "site", 300);
    p.departmentId = id(v, "departmentId");
    db.get(Department.class, p.departmentId);
    access.department(p.departmentId);
    p.managerId = account(id(v, "managerId"), "project.write", p.departmentId).id;
    if (v.get("clientId") != null && !v.get("clientId").toString().isBlank()) {
      var a = account(id(v, "clientId"), "client", p.departmentId);
      var r = db.get(AccessRole.class, a.roleId);
      if (!r.scope.equals("ASSIGNED")
          || !Set.of("dashboard", "project.read", "client").containsAll(r.permissions))
        throw new Problem(400, "INVALID_CLIENT_ROLE");
      p.clientId = a.id;
    }
    p.startDate = date(v, "startDate");
    p.endDate = date(v, "endDate");
    if (p.endDate.isBefore(p.startDate)) throw new Problem(400, "INVALID_DATES");
    p.status = "DRAFT";
    db.save(p);
    changed(p, "PROJECT_CREATE", p.id);
    return p;
  }

  /** 修改草稿合同描述和工期，责任人及部门绑定保持稳定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Project editProject(Long pid, Map<String, Object> v) {
    var p = project(pid, true);
    internal(p, "project.write");
    revision(p, v);
    if (!p.status.equals("DRAFT")) throw new Problem(409, "CONTRACT_FROZEN");
    var start = date(v, "startDate");
    var end = date(v, "endDate");
    if (end.isBefore(start)
        || works(p).stream().anyMatch(w -> w.dueDate.isBefore(start) || w.dueDate.isAfter(end)))
      throw new Problem(400, "INVALID_DATES");
    p.code = text(v, "code", 60);
    p.name = text(v, "name", 120);
    p.customer = text(v, "customer", 120);
    p.site = text(v, "site", 300);
    p.startDate = start;
    p.endDate = end;
    changed(p, "PROJECT_EDIT", p.id);
    return p;
  }

  /** 未发生任何清单和成员引用的草稿可删除，其审计仍保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteProject(Long pid, Map<String, Object> v) {
    var p = project(pid, true);
    internal(p, "project.write");
    revision(p, v);
    if (!p.status.equals("DRAFT")) throw new Problem(409, "CONTRACT_FROZEN");
    if (!works(p).isEmpty()
        || !db.query(ProjectMember.class, "from ProjectMember where projectId=?1", p.id).isEmpty())
      throw new Problem(409, "REFERENCED_RECORD");
    access.audit("PROJECT_DELETE", p.id, p.departmentId);
    db.delete(p);
  }

  /** 草稿工程量清单可录入和修改；合同激活后通过增减项改变数量与预算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public WorkItem saveWork(Long projectId, Long workId, Map<String, Object> v) {
    var p = project(projectId, true);
    internal(p, "project.write");
    revision(p, v);
    if (!p.status.equals("DRAFT")) throw new Problem(409, "CONTRACT_FROZEN");
    var w = workId == null ? new WorkItem() : work(p, workId);
    w.projectId = p.id;
    w.code = text(v, "code", 60);
    w.name = text(v, "name", 120);
    w.unit = text(v, "unit", 30);
    w.quantity = number(v, "quantity", 4, true);
    w.price = number(v, "price", 2, false);
    w.subPrice = number(v, "subPrice", 2, false);
    w.costBudget = number(v, "costBudget", 2, false);
    if (w.price.signum() < 0 || w.subPrice.signum() < 0 || w.costBudget.signum() < 0)
      throw new Problem(400, "INVALID_AMOUNT");
    w.vendor = (String) v.getOrDefault("vendor", "");
    if (w.vendor == null
        || w.vendor.length() > 120
        || w.subPrice.signum() > 0 && w.vendor.isBlank()) throw new Problem(400, "INVALID_VENDOR");
    w.dueDate = date(v, "dueDate");
    if (w.dueDate.isBefore(p.startDate) || w.dueDate.isAfter(p.endDate))
      throw new Problem(400, "INVALID_DATES");
    if (workId == null) db.save(w);
    changed(p, "WORK_SAVE", w.id);
    return w;
  }

  /** 删除未激活草稿项；不能删除已发生业务的工程量记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteWork(Long pid, Long wid, Map<String, Object> v) {
    var p = project(pid, true);
    internal(p, "project.write");
    revision(p, v);
    if (!p.status.equals("DRAFT")) throw new Problem(409, "CONTRACT_FROZEN");
    db.delete(work(p, wid));
    changed(p, "WORK_DELETE", wid);
  }

  /** 添加或移除现场成员，客户不能成为内部成员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void member(Long pid, Map<String, Object> v) {
    var p = project(pid, true);
    internal(p, "project.write");
    revision(p, v);
    if (p.status.equals("CLOSED")) throw new Problem(409, "PROJECT_CLOSED");
    var a = account(id(v, "accountId"), "site", p.departmentId);
    if (db.get(AccessRole.class, a.roleId).permissions.contains("client"))
      throw new Problem(400, "INVALID_ASSIGNEE");
    var existing =
        db.query(
            ProjectMember.class,
            "from ProjectMember where projectId=?1 and accountId=?2",
            p.id,
            a.id);
    if (Boolean.TRUE.equals(v.get("remove"))) {
      existing.forEach(db::delete);
    } else if (existing.isEmpty()) {
      var m = new ProjectMember();
      m.projectId = p.id;
      m.accountId = a.id;
      db.save(m);
    }
    changed(p, "MEMBER_CHANGE", a.id);
  }

  /** 激活合同冻结初始清单；完工必须全量验收、待审核清零、保留款释放且账款清零。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Project state(Long pid, String action, Map<String, Object> v) {
    var p = project(pid, true);
    internal(p, "project.write");
    revision(p, v);
    if (action.equals("activate")) {
      if (!p.status.equals("DRAFT") || works(p).isEmpty()) throw new Problem(409, "INVALID_STATE");
      p.status = "ACTIVE";
    } else if (action.equals("close")) {
      active(p);
      for (var w : works(p)) {
        if (accepted(w).compareTo(w.quantity) != 0) throw new Problem(409, "UNACCEPTED_WORK");
        if (billedQuantity(w, "CUSTOMER").compareTo(w.quantity) != 0 && w.price.signum() > 0)
          throw new Problem(409, "UNBILLED_WORK");
        if (billedQuantity(w, "SUBCONTRACT").compareTo(w.quantity) != 0 && w.subPrice.signum() > 0)
          throw new Problem(409, "UNBILLED_WORK");
      }
      if (db.query(
                      ProgressClaim.class,
                      "from ProgressClaim where projectId=?1 and status='PENDING'",
                      p.id)
                  .size()
              > 0
          || db.query(
                      Variation.class,
                      "from Variation where projectId=?1 and status='PENDING'",
                      p.id)
                  .size()
              > 0
          || db.query(
                      CostEntry.class,
                      "from CostEntry where projectId=?1 and status='PENDING'",
                      p.id)
                  .size()
              > 0
          || bills(p).stream().anyMatch(b -> b.status.equals("PENDING")))
        throw new Problem(409, "PENDING_REVIEW");
      if (bills(p).stream()
          .filter(b -> b.status.equals("APPROVED"))
          .anyMatch(b -> !b.released && b.retention.signum() > 0 || balance(b).signum() != 0))
        throw new Problem(409, "UNSETTLED_BILL");
      p.status = "CLOSED";
    } else throw new Problem(404, "NOT_FOUND");
    changed(p, "PROJECT_" + action.toUpperCase(), p.id);
    return p;
  }

  /** 现场申报量必须在批准总量内，其他待审申报同时占用容量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ProgressClaim claim(Long pid, Map<String, Object> v) {
    var p = project(pid, true);
    internal(p, "site");
    revision(p, v);
    active(p);
    var w = work(p, id(v, "workId"));
    var c = new ProgressClaim();
    c.projectId = p.id;
    c.workId = w.id;
    c.quantity = number(v, "quantity", 4, true);
    BuildPolicy.capacity(reserved(w).add(c.quantity), w.quantity);
    c.note = text(v, "note", 1000);
    c.workDate = date(v, "workDate");
    if (c.workDate.isBefore(p.startDate) || c.workDate.isAfter(LocalDate.now(clock)))
      throw new Problem(400, "INVALID_DATES");
    c.status = "PENDING";
    c.creatorId = access.current().id;
    c.reviewNote = "";
    c.createdAt = clock.instant();
    db.save(c);
    changed(p, "CLAIM_SUBMIT", c.id);
    return c;
  }

  /** 增减项保留原清单、审批引用与前后预算；提交不改变合同。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Variation variation(Long pid, Map<String, Object> v) {
    var p = project(pid, true);
    internal(p, "project.write");
    revision(p, v);
    active(p);
    var w = work(p, id(v, "workId"));
    var x = new Variation();
    x.projectId = p.id;
    x.workId = w.id;
    x.quantityDelta = number(v, "quantityDelta", 4, false);
    x.budgetDelta = number(v, "budgetDelta", 2, false);
    if (x.quantityDelta.signum() == 0 && x.budgetDelta.signum() == 0)
      throw new Problem(400, "EMPTY_CHANGE");
    x.reason = text(v, "reason", 1000);
    x.reference = text(v, "reference", 120);
    x.status = "PENDING";
    x.creatorId = access.current().id;
    x.reviewNote = "";
    x.createdAt = clock.instant();
    db.save(x);
    changed(p, "CHANGE_SUBMIT", x.id);
    return x;
  }

  /** 记录材料、人工或其他直接成本；分包结算单单独计成本，避免重复录入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public CostEntry cost(Long pid, Map<String, Object> v) {
    var p = project(pid, true);
    internal(p, "cost");
    revision(p, v);
    active(p);
    var w = work(p, id(v, "workId"));
    var x = new CostEntry();
    x.projectId = p.id;
    x.workId = w.id;
    x.category = text(v, "category", 60);
    if (db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type='cost' and code=?1 and enabled=true",
            x.category)
        .isEmpty()) throw new Problem(400, "INVALID_CATEGORY");
    x.amount = number(v, "amount", 2, false);
    if (x.amount.signum() == 0) throw new Problem(400, "INVALID_AMOUNT");
    if (v.get("originalId") != null && !v.get("originalId").toString().isBlank()) {
      x.originalId = id(v, "originalId");
      var original = db.get(CostEntry.class, x.originalId);
      if (!Objects.equals(original.projectId, p.id)
          || !Objects.equals(original.workId, w.id)
          || !original.status.equals("APPROVED")
          || original.originalId != null
          || x.amount.signum() >= 0) throw new Problem(409, "INVALID_COST_ADJUSTMENT");
      var adjustments =
          db
              .query(
                  CostEntry.class,
                  "from CostEntry where originalId=?1 and status<>'REJECTED'",
                  original.id)
              .stream()
              .map(a -> a.amount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      if (original.amount.add(adjustments).add(x.amount).signum() < 0)
        throw new Problem(409, "INVALID_COST_ADJUSTMENT");
      x.category = original.category;
    } else if (x.amount.signum() < 0) throw new Problem(400, "ORIGINAL_REQUIRED");
    x.reference = text(v, "reference", 120);
    x.note = text(v, "note", 1000);
    x.status = "PENDING";
    x.creatorId = access.current().id;
    x.reviewNote = "";
    x.createdAt = clock.instant();
    db.save(x);
    changed(p, "COST_SUBMIT", x.id);
    return x;
  }

  /** 结算量取验收记录且防止重复结算，金额及保留款由冻结单价计算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Billing bill(Long pid, Map<String, Object> v) {
    var p = project(pid, true);
    internal(p, "cost");
    revision(p, v);
    active(p);
    var w = work(p, id(v, "workId"));
    var b = new Billing();
    b.projectId = p.id;
    b.workId = w.id;
    b.kind = text(v, "kind", 20);
    if (!Set.of("CUSTOMER", "SUBCONTRACT").contains(b.kind)) throw new Problem(400, "INVALID_KIND");
    b.quantity = number(v, "quantity", 4, true);
    BuildPolicy.capacity(billedQuantity(w, b.kind).add(b.quantity), accepted(w));
    b.gross = BuildPolicy.amount(b.quantity, b.kind.equals("CUSTOMER") ? w.price : w.subPrice);
    if (b.gross.signum() <= 0) throw new Problem(400, "ZERO_BILL");
    b.retention = BuildPolicy.retention(b.gross, number(v, "retentionPercent", 2, false));
    b.released = b.retention.signum() == 0;
    b.dueDate = date(v, "dueDate");
    b.reference = text(v, "reference", 120);
    b.status = "PENDING";
    b.creatorId = access.current().id;
    b.reviewNote = "";
    b.createdAt = clock.instant();
    db.save(b);
    changed(p, "BILL_SUBMIT", b.id);
    return b;
  }

  /** 审核必须独立于提交人；退回保留原记录，批准后不覆写。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object review(Long pid, String type, Long rid, Map<String, Object> v) {
    var p = project(pid, true);
    internal(p, Set.of("costs", "bills").contains(type) ? "finance" : "review");
    revision(p, v);
    active(p);
    boolean approve = Boolean.TRUE.equals(v.get("approve"));
    String review = text(v, "reviewNote", 1000);
    Long actor = access.current().id;
    Object result;
    switch (type) {
      case "claims" -> {
        var x = db.get(ProgressClaim.class, rid);
        checkReview(p, x.projectId, x.creatorId, x.status);
        if (approve)
          BuildPolicy.capacity(
              accepted(work(p, x.workId)).add(x.quantity), work(p, x.workId).quantity);
        x.status = approve ? "APPROVED" : "REJECTED";
        x.reviewerId = actor;
        x.reviewNote = review;
        result = x;
      }
      case "variations" -> {
        var x = db.get(Variation.class, rid);
        checkReview(p, x.projectId, x.creatorId, x.status);
        if (approve) {
          var w = work(p, x.workId);
          var q = w.quantity.add(x.quantityDelta);
          if (q.signum() <= 0 || w.costBudget.add(x.budgetDelta).signum() < 0)
            throw new Problem(409, "INVALID_CHANGE");
          BuildPolicy.capacity(reserved(w), q);
          BuildPolicy.capacity(billedQuantity(w, "CUSTOMER"), q);
          BuildPolicy.capacity(billedQuantity(w, "SUBCONTRACT"), q);
          w.quantity = q;
          w.costBudget = w.costBudget.add(x.budgetDelta);
        }
        x.status = approve ? "APPROVED" : "REJECTED";
        x.reviewerId = actor;
        x.reviewNote = review;
        result = x;
      }
      case "costs" -> {
        var x = db.get(CostEntry.class, rid);
        checkReview(p, x.projectId, x.creatorId, x.status);
        x.status = approve ? "APPROVED" : "REJECTED";
        x.reviewerId = actor;
        x.reviewNote = review;
        result = x;
      }
      case "bills" -> {
        var x = db.get(Billing.class, rid);
        checkReview(p, x.projectId, x.creatorId, x.status);
        if (approve)
          BuildPolicy.capacity(
              billedQuantity(work(p, x.workId), x.kind), accepted(work(p, x.workId)));
        x.status = approve ? "APPROVED" : "REJECTED";
        x.reviewerId = actor;
        x.reviewNote = review;
        result = x;
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    changed(p, "REVIEW_" + type.toUpperCase(), rid);
    return result;
  }

  private void checkReview(Project p, Long pid, Long creator, String status) {
    if (!Objects.equals(p.id, pid)) throw new Problem(400, "WRONG_PROJECT");
    if (!status.equals("PENDING")) throw new Problem(409, "INVALID_STATE");
    if (Objects.equals(creator, access.current().id))
      throw new Problem(409, "INDEPENDENT_REVIEW_REQUIRED");
  }

  /** 释放约定保留款必须工程量全数验收并填写合同引用；不会实际转账。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Billing release(Long pid, Long bid, Map<String, Object> v) {
    var p = project(pid, true);
    internal(p, "finance");
    revision(p, v);
    active(p);
    var b = db.get(Billing.class, bid);
    if (!Objects.equals(b.projectId, p.id) || !b.status.equals("APPROVED") || b.released)
      throw new Problem(409, "INVALID_STATE");
    if (works(p).stream().anyMatch(w -> accepted(w).compareTo(w.quantity) != 0))
      throw new Problem(409, "UNACCEPTED_WORK");
    b.releaseReference = text(v, "reference", 120);
    b.releaseActorId = access.current().id;
    b.releasedAt = clock.instant();
    b.released = true;
    changed(p, "RETENTION_RELEASE", bid);
    return b;
  }

  /** 记录已完成线下交易；关联冲正、退款和幂等指纹阻止重复及超额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public MoneyEntry money(Long pid, Long bid, Map<String, Object> v) {
    var p = project(pid, true);
    internal(p, "finance");
    active(p);
    var b = db.get(Billing.class, bid);
    if (!Objects.equals(b.projectId, p.id) || !b.status.equals("APPROVED"))
      throw new Problem(409, "INVALID_STATE");
    var key = text(v, "requestKey", 80);
    var kind = text(v, "kind", 20);
    var amount = number(v, "amount", 2, true);
    var ref = text(v, "reference", 120);
    var description = text(v, "note", 1000);
    Long original =
        v.get("originalId") == null || v.get("originalId").toString().isBlank()
            ? null
            : id(v, "originalId");
    String fp =
        fingerprint(
            pid
                + "|"
                + bid
                + "|"
                + kind
                + "|"
                + amount
                + "|"
                + original
                + "|"
                + ref
                + "|"
                + description);
    var prior = db.query(MoneyEntry.class, "from MoneyEntry where requestKey=?1", key);
    if (!prior.isEmpty()) {
      var e = prior.getFirst();
      if (!Objects.equals(e.actorId, access.current().id) || !e.fingerprint.equals(fp))
        throw new Problem(409, "KEY_REUSED");
      return e;
    }
    revision(p, v);
    var net = net(b);
    var due = due(b);
    if (kind.equals("PAYMENT")) {
      if (original != null) throw new Problem(400, "INVALID_ORIGINAL");
      if (amount.compareTo(due.subtract(net)) > 0) throw new Problem(409, "OVERPAYMENT");
    } else if (kind.equals("REFUND")) {
      if (original == null) throw new Problem(400, "ORIGINAL_REQUIRED");
      var o = db.get(MoneyEntry.class, original);
      if (!Objects.equals(o.billId, b.id)
          || !o.kind.equals("PAYMENT")
          || amount.compareTo(remaining(o)) > 0
          || amount.compareTo(net) > 0) throw new Problem(409, "INVALID_REFUND");
    } else if (kind.equals("REVERSAL")) {
      if (original == null) throw new Problem(400, "ORIGINAL_REQUIRED");
      var o = db.get(MoneyEntry.class, original);
      if (!Objects.equals(o.billId, b.id)
          || !Set.of("PAYMENT", "REFUND").contains(o.kind)
          || !amount.equals(o.amount)
          || !db.query(MoneyEntry.class, "from MoneyEntry where originalId=?1", o.id).isEmpty())
        throw new Problem(409, "INVALID_REVERSAL");
      var next = o.kind.equals("PAYMENT") ? net.subtract(amount) : net.add(amount);
      if (next.signum() < 0 || next.compareTo(due) > 0) throw new Problem(409, "INVALID_REVERSAL");
    } else throw new Problem(400, "INVALID_KIND");
    var e = new MoneyEntry();
    e.projectId = p.id;
    e.billId = b.id;
    e.kind = kind;
    e.amount = amount;
    e.originalId = original;
    e.reference = ref;
    e.note = description;
    e.requestKey = key;
    e.fingerprint = fp;
    e.actorId = access.current().id;
    e.createdAt = clock.instant();
    db.save(e);
    changed(p, "MONEY_" + kind, e.id);
    return e;
  }

  private String fingerprint(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private BigDecimal remaining(MoneyEntry e) {
    if (db.query(MoneyEntry.class, "from MoneyEntry where originalId=?1 and kind='REVERSAL'", e.id)
            .size()
        > 0) return BigDecimal.ZERO;
    var refunds =
        db
            .query(MoneyEntry.class, "from MoneyEntry where originalId=?1 and kind='REFUND'", e.id)
            .stream()
            .filter(
                x ->
                    db.query(
                            MoneyEntry.class,
                            "from MoneyEntry where originalId=?1 and kind='REVERSAL'",
                            x.id)
                        .isEmpty())
            .map(x -> x.amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return e.amount.subtract(refunds);
  }

  /** 验收记录允许提交人上传实际PNG/JPEG，内容存库并随备份持久化。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public SiteAttachment attach(Long pid, Long cid, String filename, byte[] bytes) {
    var p = project(pid, true);
    internal(p, "site");
    active(p);
    var c = db.get(ProgressClaim.class, cid);
    if (!Objects.equals(c.projectId, p.id)
        || !Objects.equals(c.creatorId, access.current().id)
        || !c.status.equals("PENDING")) throw new Problem(403, "FORBIDDEN");
    if (bytes.length < 16 || bytes.length > 2097152) throw new Problem(400, "INVALID_FILE");
    String type;
    if (bytes[0] == (byte) 137
        && bytes[1] == 80
        && bytes[2] == 78
        && bytes[3] == 71
        && bytes[4] == 13
        && bytes[5] == 10
        && bytes[6] == 26
        && bytes[7] == 10) type = "image/png";
    else if (bytes[0] == (byte) 255 && bytes[1] == (byte) 216 && bytes[2] == (byte) 255)
      type = "image/jpeg";
    else throw new Problem(400, "INVALID_FILE");
    try {
      try (var input =
          javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
        var readers = javax.imageio.ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw new Problem(400, "INVALID_FILE");
        var reader = readers.next();
        try {
          reader.setInput(input);
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (width > 8000
              || height > 8000
              || ((long) width) * height > 20000000
              || reader.read(0) == null) throw new Problem(400, "INVALID_FILE");
        } finally {
          reader.dispose();
        }
      }
    } catch (java.io.IOException e) {
      throw new Problem(400, "INVALID_FILE");
    }
    if (db.query(SiteAttachment.class, "from SiteAttachment where claimId=?1", c.id).size() >= 10)
      throw new Problem(413, "RESOURCE_LIMIT");
    var a = new SiteAttachment();
    a.projectId = p.id;
    a.claimId = c.id;
    a.filename =
        AdminService.text(filename, 120).replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
    a.mediaType = type;
    a.content = bytes;
    a.creatorId = access.current().id;
    a.createdAt = clock.instant();
    db.save(a);
    changed(p, "ATTACHMENT_CREATE", a.id);
    return a;
  }

  /** 附件只通过身份与项目范围检查后下载。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public SiteAttachment attachment(Long id) {
    var a = db.get(SiteAttachment.class, id);
    var p = project(a.projectId, false);
    if (!visible(p)) throw new Problem(403, "OUT_OF_SCOPE");
    return a;
  }

  private List<WorkItem> works(Project p) {
    return db.query(WorkItem.class, "from WorkItem where projectId=?1", p.id);
  }

  private List<Billing> bills(Project p) {
    return db.query(Billing.class, "from Billing where projectId=?1", p.id);
  }

  private BigDecimal accepted(WorkItem w) {
    return db
        .query(
            ProgressClaim.class, "from ProgressClaim where workId=?1 and status='APPROVED'", w.id)
        .stream()
        .map(c -> c.quantity)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal reserved(WorkItem w) {
    return db
        .query(
            ProgressClaim.class, "from ProgressClaim where workId=?1 and status<>'REJECTED'", w.id)
        .stream()
        .map(c -> c.quantity)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal billedQuantity(WorkItem w, String kind) {
    return db
        .query(
            Billing.class,
            "from Billing where workId=?1 and kind=?2 and status<>'REJECTED'",
            w.id,
            kind)
        .stream()
        .map(c -> c.quantity)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** 结算单累计净收/付，冲正引用原流水方向。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BigDecimal net(Billing b) {
    var sum = BigDecimal.ZERO;
    for (var e : db.query(MoneyEntry.class, "from MoneyEntry where billId=?1", b.id)) {
      int sign =
          e.kind.equals("PAYMENT")
              ? 1
              : e.kind.equals("REFUND")
                  ? -1
                  : db.get(MoneyEntry.class, e.originalId).kind.equals("PAYMENT") ? -1 : 1;
      sum = sum.add(e.amount.multiply(BigDecimal.valueOf(sign)));
    }
    return sum;
  }

  private BigDecimal due(Billing b) {
    return b.gross.subtract(b.released ? BigDecimal.ZERO : b.retention);
  }

  private BigDecimal balance(Billing b) {
    return due(b).subtract(net(b));
  }

  /** 项目财务摘要按批准合同、已确认成本和批准分包计量计算；现金流另计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> summary(Project p) {
    var items = works(p);
    BigDecimal contract = BigDecimal.ZERO,
        budget = BigDecimal.ZERO,
        sub = BigDecimal.ZERO,
        earned = BigDecimal.ZERO;
    for (var w : items) {
      contract = contract.add(BuildPolicy.amount(w.quantity, w.price));
      budget = budget.add(w.costBudget).add(BuildPolicy.amount(w.quantity, w.subPrice));
      sub = sub.add(BuildPolicy.amount(w.quantity, w.subPrice));
      earned = earned.add(BuildPolicy.amount(accepted(w), w.price));
    }
    var cost =
        db
            .query(CostEntry.class, "from CostEntry where projectId=?1 and status='APPROVED'", p.id)
            .stream()
            .map(x -> x.amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal receivable = BigDecimal.ZERO,
        payable = BigDecimal.ZERO,
        received = BigDecimal.ZERO,
        paid = BigDecimal.ZERO,
        hold = BigDecimal.ZERO;
    for (var b : bills(p)) {
      if (!b.status.equals("APPROVED")) continue;
      if (b.kind.equals("CUSTOMER")) {
        receivable = receivable.add(balance(b));
        received = received.add(net(b));
      } else {
        cost = cost.add(b.gross);
        payable = payable.add(balance(b));
        paid = paid.add(net(b));
      }
      if (!b.released) hold = hold.add(b.retention);
    }
    var v = new LinkedHashMap<String, Object>();
    v.put("project", p);
    v.put("contract", contract);
    v.put("budget", budget);
    v.put("earned", earned);
    v.put("actualCost", cost);
    v.put("recognizedMargin", earned.subtract(cost));
    v.put("plannedMargin", contract.subtract(budget));
    v.put("receivable", receivable);
    v.put("payable", payable);
    v.put("received", received);
    v.put("paid", paid);
    v.put("retained", hold);
    v.put("subcontractCommitment", sub);
    return v;
  }

  /** 按岗位返回详情，客户和现场响应不泄露成本/分包/资金/账号目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    var p = project(id, false);
    boolean client = isClient();
    boolean site = access.role().scope.equals("ASSIGNED") && !client;
    boolean financial =
        access.role().permissions.contains("cost")
            || access.role().permissions.contains("finance")
            || access.role().permissions.contains("report");
    var v = new LinkedHashMap<String, Object>();
    v.put("project", p);
    var rows = new ArrayList<Map<String, Object>>();
    for (var w : works(p)) {
      var m = new LinkedHashMap<String, Object>();
      m.put("id", w.id);
      m.put("code", w.code);
      m.put("name", w.name);
      m.put("unit", w.unit);
      m.put("quantity", w.quantity);
      m.put("accepted", accepted(w));
      m.put("pending", reserved(w).subtract(accepted(w)));
      m.put("dueDate", w.dueDate);
      if (!site) m.put("price", w.price);
      if (financial && !client && !site) {
        m.put("subPrice", w.subPrice);
        m.put("vendor", w.vendor);
        m.put("costBudget", w.costBudget);
        m.put("customerBilled", billedQuantity(w, "CUSTOMER"));
        m.put("subcontractBilled", billedQuantity(w, "SUBCONTRACT"));
      }
      rows.add(m);
    }
    v.put("works", rows);
    v.put("claims", db.query(ProgressClaim.class, "from ProgressClaim where projectId=?1", p.id));
    v.put(
        "attachments",
        db.query(SiteAttachment.class, "from SiteAttachment where projectId=?1", p.id));
    if (!site) {
      v.put(
          "bills",
          bills(p).stream()
              .filter(b -> !client || b.kind.equals("CUSTOMER"))
              .map(
                  b -> {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("bill", b);
                    m.put("net", net(b));
                    m.put("due", due(b));
                    m.put("balance", balance(b));
                    return m;
                  })
              .toList());
    }
    if (!site && !client) {
      v.put("variations", db.query(Variation.class, "from Variation where projectId=?1", p.id));
      v.put(
          "members", db.query(ProjectMember.class, "from ProjectMember where projectId=?1", p.id));
      if (financial) {
        v.put("costs", db.query(CostEntry.class, "from CostEntry where projectId=?1", p.id));
        v.put("money", db.query(MoneyEntry.class, "from MoneyEntry where projectId=?1", p.id));
        v.put("summary", summary(p));
      }
    }
    return v;
  }

  /** 安全目录只返回内部岗位所需的可见账号，不返回密码或其他部门客户。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> catalog() {
    var r = access.role();
    var v = new LinkedHashMap<String, Object>();
    v.put("settings", db.all(SystemSetting.class));
    if (r.permissions.contains("project.write") && !r.scope.equals("ASSIGNED")) {
      v.put(
          "departments",
          db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList());
      v.put(
          "accounts",
          db.all(Account.class).stream()
              .filter(a -> a.enabled && access.visible(a.departmentId))
              .map(
                  a ->
                      Map.of(
                          "id",
                          a.id,
                          "name",
                          a.displayName,
                          "departmentId",
                          a.departmentId,
                          "permissions",
                          db.get(AccessRole.class, a.roleId).permissions))
              .toList());
    }
    if (!(r.scope.equals("ASSIGNED") && r.permissions.contains("client")))
      v.put("dictionaries", db.all(DictionaryEntry.class).stream().filter(x -> x.enabled).toList());
    return v;
  }

  /** 成本统计按相同数据范围计算；不向客户/现场返回财务摘要。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> report() {
    access.require("report");
    return db.all(Project.class).stream().filter(this::visible).map(this::summary).toList();
  }

  /** 操作审计仅有审计权限的账号查看部门范围，不支持改写。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<?> audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(a -> access.visible(a.departmentId))
        .sorted(Comparator.comparing((AuditEvent a) -> a.id).reversed())
        .toList();
  }
}

// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.buildflow;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 工程业务与系统管理的有限API，权限检查在服务层执行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final BuildService business;
  final AdminService admin;

  public ApiController(BuildService business, AdminService admin) {
    this.business = business;
    this.admin = admin;
  }

  /** 查看可见项目。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/projects")
  public List<?> projects() {
    return business.projects();
  }

  /** 创建工程合同草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects")
  public Object create(@RequestBody Map<String, Object> v) {
    return business.create(v);
  }

  /** 修改草稿合同。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/projects/{id}")
  public Object editProject(@PathVariable Long id, @RequestBody Map<String, Object> v) {
    return business.editProject(id, v);
  }

  /** 删除没有业务引用的草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/projects/{id}")
  public Object deleteProject(@PathVariable Long id, @RequestBody Map<String, Object> v) {
    business.deleteProject(id, v);
    return Map.of("ok", true);
  }

  /** 查看岗位过滤后的项目详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/projects/{id}")
  public Object detail(@PathVariable Long id) {
    return business.detail(id);
  }

  /** 保存草稿清单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/works")
  public Object work(@PathVariable Long id, @RequestBody Map<String, Object> v) {
    return business.saveWork(id, null, v);
  }

  /** 更新草稿项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/projects/{id}/works/{wid}")
  public Object edit(
      @PathVariable Long id, @PathVariable Long wid, @RequestBody Map<String, Object> v) {
    return business.saveWork(id, wid, v);
  }

  /** 删除未激活草稿项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/projects/{id}/works/{wid}")
  public Object delete(
      @PathVariable Long id, @PathVariable Long wid, @RequestBody Map<String, Object> v) {
    business.deleteWork(id, wid, v);
    return Map.of("ok", true);
  }

  /** 激活或关闭合同。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/state/{action}")
  public Object state(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> v) {
    return business.state(id, action, v);
  }

  /** 设置现场成员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/members")
  public Object member(@PathVariable Long id, @RequestBody Map<String, Object> v) {
    business.member(id, v);
    return Map.of("ok", true);
  }

  /** 申报工程量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/claims")
  public Object claim(@PathVariable Long id, @RequestBody Map<String, Object> v) {
    return business.claim(id, v);
  }

  /** 提交合同增减项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/variations")
  public Object variation(@PathVariable Long id, @RequestBody Map<String, Object> v) {
    return business.variation(id, v);
  }

  /** 提交实际成本凭据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/costs")
  public Object cost(@PathVariable Long id, @RequestBody Map<String, Object> v) {
    return business.cost(id, v);
  }

  /** 提交分期结算单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/bills")
  public Object bill(@PathVariable Long id, @RequestBody Map<String, Object> v) {
    return business.bill(id, v);
  }

  /** 独立审核或退回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/{type}/{rid}/review")
  public Object review(
      @PathVariable Long id,
      @PathVariable String type,
      @PathVariable Long rid,
      @RequestBody Map<String, Object> v) {
    return business.review(id, type, rid, v);
  }

  /** 释放合同保留款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/bills/{bid}/release")
  public Object release(
      @PathVariable Long id, @PathVariable Long bid, @RequestBody Map<String, Object> v) {
    return business.release(id, bid, v);
  }

  /** 线下收付流水及关联退款冲正。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/bills/{bid}/money")
  public Object money(
      @PathVariable Long id, @PathVariable Long bid, @RequestBody Map<String, Object> v) {
    return business.money(id, bid, v);
  }

  /** 上传有内容校验的工程图片。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/projects/{id}/claims/{cid}/attachments")
  public Object upload(
      @PathVariable Long id, @PathVariable Long cid, @RequestParam("file") MultipartFile file)
      throws java.io.IOException {
    return business.attach(id, cid, file.getOriginalFilename(), file.getBytes());
  }

  /** 下载有项目范围的图片。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/attachments/{id}")
  public ResponseEntity<byte[]> attachment(@PathVariable Long id) {
    var a = business.attachment(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(a.mediaType))
        .header("X-Content-Type-Options", "nosniff")
        .header("Cache-Control", "private, no-store")
        .body(a.content);
  }

  /** 读取岗位基础目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/catalog")
  public Object catalog() {
    return business.catalog();
  }

  /** 可见项目成本报告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  public Object report() {
    return business.report();
  }

  /** 下载成本CSV；文本字段中和公式注入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports.csv")
  public ResponseEntity<String> csv() {
    var b =
        new StringBuilder(
            "\ufeffProject,Contract,Budget,Earned,Actual cost,Recognized margin,Receivable,Payable,Received,Paid\r\n");
    for (var row : business.report()) {
      var p = (Project) row.get("project");
      var code = p.code.replace("\"", "\"\"");
      if (code.matches("^[=+\\-@].*")) code = "'" + code;
      b.append("\"").append(code).append("\"");
      for (var key :
          List.of(
              "contract",
              "budget",
              "earned",
              "actualCost",
              "recognizedMargin",
              "receivable",
              "payable",
              "received",
              "paid")) b.append(',').append(row.get(key));
      b.append("\r\n");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header("Content-Disposition", "attachment; filename=buildflow-costs.csv")
        .body(b.toString());
  }

  /** 查看不可修改的审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return business.audit();
  }

  /** 管理资源列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object list(@PathVariable String type) {
    return admin.list(type);
  }

  /** 新增管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object add(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 修改角色、菜单及账号等。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object update(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 外键和最后管理员保护下删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object remove(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}

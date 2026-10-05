// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.buildflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 首次空库初始化岗位和管理配置，不生成业务合同。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;

  @Value("${buildflow.admin-username}")
  String username;

  @Value("${buildflow.admin-password}")
  String password;

  public Bootstrap(Store db, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.encoder = encoder;
  }

  /** 只在空账号库初始化，重启不改密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}"))
      throw new IllegalArgumentException("INVALID_ADMIN_USERNAME");
    var d = new Department();
    d.name = "工程运营 / Project operations";
    db.save(d);
    String[][] defs = {
      {"dashboard", "工作台 / Workspace"},
      {"project.read", "查看项目 / Read projects"},
      {"project.write", "合同与工程量管理 / Manage projects"},
      {"site", "现场申报 / Site claims"},
      {"review", "独立验收与变更审批 / Independent review"},
      {"cost", "成本与结算申请 / Cost and billing"},
      {"finance", "审核结算与登记资金 / Finance"},
      {"client", "本人项目入口 / Client portal"},
      {"report", "成本报告 / Reports"},
      {"admin", "账号与系统管理 / Administration"},
      {"audit", "操作审计 / Audit"}
    };
    var all = new HashSet<String>();
    for (var v : defs) {
      var p = new Permission();
      p.code = v[0];
      p.name = v[1];
      db.save(p);
      all.add(v[0]);
    }
    all.remove("client");
    var admin = role("管理员 / Administrator", "ALL", all);
    role(
        "项目经理 / Project manager",
        "DEPARTMENT",
        Set.of("dashboard", "project.read", "project.write", "site", "review", "cost", "report"));
    role("现场人员 / Site worker", "ASSIGNED", Set.of("dashboard", "project.read", "site"));
    role("财务 / Finance", "DEPARTMENT", Set.of("dashboard", "project.read", "finance", "report"));
    role("客户 / Client", "ASSIGNED", Set.of("dashboard", "project.read", "client"));
    var a = new Account();
    a.username = username;
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.roleId = admin.id;
    a.departmentId = d.id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"dashboard", "工作台", "Workspace", "dashboard"},
      {"projects", "工程项目", "Projects", "project.read"},
      {"finance", "收付款", "Settlement", "finance"},
      {"reports", "项目成本", "Job costing", "report"},
      {"admin", "账号与设置", "Administration", "admin"},
      {"audit", "操作记录", "Audit", "audit"}
    };
    int pos = 0;
    for (var v : menus) {
      var m = new NavMenu();
      m.code = v[0];
      m.name = v[1];
      m.nameEn = v[2];
      m.permissionCode = v[3];
      m.position = pos++;
      m.enabled = true;
      db.save(m);
    }
    for (var v :
        new String[][] {{"currency", "CNY"}, {"companyName", "工程运营 / Project operations"}}) {
      var s = new SystemSetting();
      s.code = v[0];
      s.value = v[1];
      db.save(s);
    }
    for (var v :
        new String[][] {
          {"cost", "MATERIAL", "材料", "Materials"},
          {"cost", "LABOR", "人工", "Labor"},
          {"cost", "OTHER", "其他直接成本", "Other direct cost"}
        }) {
      var e = new DictionaryEntry();
      e.type = v[0];
      e.code = v[1];
      e.name = v[2];
      e.nameEn = v[3];
      e.enabled = true;
      db.save(e);
    }
  }

  private AccessRole role(String name, String scope, Set<String> permissions) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(permissions);
    return db.save(r);
  }
}

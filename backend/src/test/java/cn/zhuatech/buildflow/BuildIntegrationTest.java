// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.buildflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 实际HTTP与JPA集成：完整结算、独立审批、权限数据范围及关联退款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
class BuildIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:build;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("buildflow.admin-password", () -> PASSWORD);
  }

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, manager, fin, site, client, other;
  long pid, wid, managerId, siteId, clientId;
  String suffix;

  @BeforeEach
  void setup() throws Exception {
    suffix = UUID.randomUUID().toString().substring(0, 8);
    admin = login("admin");
    var roles = getAs(admin, "/admin/roles", 200);
    long mr = 0, fr = 0, sr = 0, cr = 0;
    for (var row : roles) {
      String n = row.get("name").asString();
      if (n.contains("Project manager")) mr = row.get("id").asLong();
      if (n.contains("Finance")) fr = row.get("id").asLong();
      if (n.contains("Site worker")) sr = row.get("id").asLong();
      if (n.contains("Client")) cr = row.get("id").asLong();
    }
    managerId = user("pm" + suffix, mr, 1);
    user("fin" + suffix, fr, 1);
    siteId = user("site" + suffix, sr, 1);
    clientId = user("client" + suffix, cr, 1);
    user("other" + suffix, cr, 1);
    manager = login("pm" + suffix);
    fin = login("fin" + suffix);
    site = login("site" + suffix);
    client = login("client" + suffix);
    other = login("other" + suffix);
    pid =
        postAs(
                manager,
                "/projects",
                m(
                    "code",
                    "TEST-" + suffix,
                    "name",
                    "TEST fitout",
                    "customer",
                    "TEST customer",
                    "site",
                    "TEST site",
                    "departmentId",
                    1,
                    "managerId",
                    managerId,
                    "clientId",
                    clientId,
                    "startDate",
                    today().minusDays(3).toString(),
                    "endDate",
                    today().plusDays(10).toString()),
                200)
            .get("id")
            .asLong();
    wid =
        act(
                manager,
                "/works",
                m(
                    "code",
                    "W1",
                    "name",
                    "TEST installation",
                    "unit",
                    "m2",
                    "quantity",
                    "10.0000",
                    "price",
                    "100.00",
                    "subPrice",
                    "20.00",
                    "vendor",
                    "TEST subcontractor",
                    "costBudget",
                    "300.00",
                    "dueDate",
                    today().plusDays(9).toString()),
                200)
            .get("id")
            .asLong();
    act(manager, "/members", m("accountId", siteId), 200);
    act(manager, "/state/activate", m(), 200);
  }

  LocalDate today() {
    return LocalDate.now(ZoneOffset.UTC);
  }

  Map<String, Object> m(Object... args) {
    var v = new LinkedHashMap<String, Object>();
    for (int i = 0; i < args.length; i += 2) v.put((String) args[i], args[i + 1]);
    return v;
  }

  MockHttpSession login(String name) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(m("username", name, "password", PASSWORD))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession();
  }

  long user(String username, long role, long dept) throws Exception {
    return postAs(
            admin,
            "/admin/users",
            m(
                "username",
                username,
                "displayName",
                "TEST " + username,
                "password",
                PASSWORD,
                "roleId",
                role,
                "departmentId",
                dept,
                "enabled",
                true),
            200)
        .get("id")
        .asLong();
  }

  JsonNode getAs(MockHttpSession s, String path, int status) throws Exception {
    var r = mvc.perform(get("/api" + path).session(s)).andReturn();
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  JsonNode postAs(MockHttpSession s, String path, Map<String, Object> v, int status)
      throws Exception {
    var r =
        mvc.perform(
                post("/api" + path)
                    .session(s)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(v)))
            .andReturn();
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  JsonNode act(MockHttpSession s, String path, Map<String, Object> v, int status) throws Exception {
    v.put(
        "revision", getAs(admin, "/projects/" + pid, 200).get("project").get("revision").asLong());
    return postAs(s, "/projects/" + pid + path, v, status);
  }

  long claim(String qty) throws Exception {
    return act(
            site,
            "/claims",
            m(
                "workId",
                wid,
                "quantity",
                qty,
                "note",
                "TEST completed",
                "workDate",
                today().toString()),
            200)
        .get("id")
        .asLong();
  }

  void approve(MockHttpSession s, String type, long id) throws Exception {
    act(
        s,
        "/" + type + "/" + id + "/review",
        m("approve", true, "reviewNote", "TEST checked"),
        200);
  }

  long bill(String kind, String qty, String hold) throws Exception {
    return act(
            manager,
            "/bills",
            m(
                "workId",
                wid,
                "kind",
                kind,
                "quantity",
                qty,
                "retentionPercent",
                hold,
                "dueDate",
                today().plusDays(10).toString(),
                "reference",
                "TEST bill"),
            200)
        .get("id")
        .asLong();
  }

  JsonNode money(long bill, String kind, String amount, Long original, String key, int status)
      throws Exception {
    var v =
        m(
            "kind",
            kind,
            "amount",
            amount,
            "reference",
            "TEST transfer",
            "note",
            "TEST offline",
            "requestKey",
            key);
    if (original != null) v.put("originalId", original);
    return act(fin, "/bills/" + bill + "/money", v, status);
  }

  @Test
  void completeContractChangeAcceptanceCostsBillingPaymentAndClose() throws Exception {
    long variation =
        act(
                manager,
                "/variations",
                m(
                    "workId",
                    wid,
                    "quantityDelta",
                    "2",
                    "budgetDelta",
                    "50",
                    "reference",
                    "TEST client confirmed",
                    "reason",
                    "TEST added work"),
                200)
            .get("id")
            .asLong();
    approve(admin, "variations", variation);
    long c = claim("12");
    approve(manager, "claims", c);
    long cost =
        act(
                manager,
                "/costs",
                m(
                    "workId",
                    wid,
                    "category",
                    "MATERIAL",
                    "amount",
                    "200",
                    "reference",
                    "TEST materials",
                    "note",
                    "TEST direct cost"),
                200)
            .get("id")
            .asLong();
    approve(fin, "costs", cost);
    long cb = bill("CUSTOMER", "12", "5"), sb = bill("SUBCONTRACT", "12", "10");
    approve(fin, "bills", cb);
    approve(fin, "bills", sb);
    money(cb, "PAYMENT", "1140", null, UUID.randomUUID().toString(), 200);
    money(sb, "PAYMENT", "216", null, UUID.randomUUID().toString(), 200);
    act(manager, "/state/close", m(), 409);
    act(fin, "/bills/" + cb + "/release", m("reference", "TEST release approved"), 200);
    act(fin, "/bills/" + sb + "/release", m("reference", "TEST release approved"), 200);
    money(cb, "PAYMENT", "60", null, UUID.randomUUID().toString(), 200);
    money(sb, "PAYMENT", "24", null, UUID.randomUUID().toString(), 200);
    act(manager, "/state/close", m(), 200);
    var d = getAs(admin, "/projects/" + pid, 200);
    assertEquals("CLOSED", d.get("project").get("status").asString());
    assertEquals(760.0, d.get("summary").get("recognizedMargin").asDouble());
    assertFalse(d.get("bills").get(0).get("bill").get("releaseReference").isNull());
  }

  @Test
  void noFinancialLeakToSiteOrClient() throws Exception {
    var s = getAs(site, "/projects/" + pid, 200);
    assertNull(s.get("summary"));
    assertNull(s.get("money"));
    assertNull(s.get("works").get(0).get("price"));
    assertNull(s.get("works").get(0).get("vendor"));
    var c = getAs(client, "/projects/" + pid, 200);
    assertNull(c.get("costs"));
    assertNull(c.get("summary"));
    assertNull(c.get("works").get(0).get("subPrice"));
    assertNull(getAs(client, "/projects", 200).get(0).get("actualCost"));
    getAs(other, "/projects/" + pid, 403);
    assertEquals(0, getAs(other, "/projects", 200).size());
    getAs(site, "/reports", 403);
    getAs(client, "/admin/users", 403);
  }

  @Test
  void independentReviewAndPendingCapacity() throws Exception {
    long c = claim("6");
    act(
        site,
        "/claims",
        m("workId", wid, "quantity", "5", "note", "TEST excess", "workDate", today().toString()),
        409);
    approve(manager, "claims", c);
    long v =
        act(
                manager,
                "/variations",
                m(
                    "workId",
                    wid,
                    "quantityDelta",
                    "-5",
                    "budgetDelta",
                    "0",
                    "reference",
                    "TEST reduction",
                    "reason",
                    "TEST"),
                200)
            .get("id")
            .asLong();
    act(
        manager,
        "/variations/" + v + "/review",
        m("approve", true, "reviewNote", "TEST self"),
        409);
    act(admin, "/variations/" + v + "/review", m("approve", true, "reviewNote", "TEST"), 409);
  }

  @Test
  void staleRevisionAndFrozenRates() throws Exception {
    postAs(manager, "/projects/" + pid + "/state/close", m("revision", 0), 409);
    act(manager, "/works", m(), 409);
    act(client, "/claims", m(), 403);
  }

  @Test
  void noBillingBeforeAcceptanceOrTwice() throws Exception {
    act(
        manager,
        "/bills",
        m(
            "workId",
            wid,
            "kind",
            "CUSTOMER",
            "quantity",
            "1",
            "retentionPercent",
            "0",
            "dueDate",
            today().toString(),
            "reference",
            "TEST"),
        409);
    approve(manager, "claims", claim("10"));
    long b = bill("CUSTOMER", "10", "0");
    act(manager, "/bills/" + b + "/review", m("approve", true, "reviewNote", "TEST self"), 403);
    approve(fin, "bills", b);
    act(
        manager,
        "/bills",
        m(
            "workId",
            wid,
            "kind",
            "CUSTOMER",
            "quantity",
            "1",
            "retentionPercent",
            "0",
            "dueDate",
            today().toString(),
            "reference",
            "TEST duplicate"),
        409);
  }

  @Test
  void partialPaymentRefundReversalAndIdempotency() throws Exception {
    approve(manager, "claims", claim("10"));
    long b = bill("CUSTOMER", "10", "0");
    approve(fin, "bills", b);
    String key = UUID.randomUUID().toString();
    long payment = money(b, "PAYMENT", "500", null, key, 200).get("id").asLong();
    assertEquals(payment, money(b, "PAYMENT", "500", null, key, 200).get("id").asLong());
    money(b, "PAYMENT", "501", null, key, 409);
    money(b, "PAYMENT", "501", null, UUID.randomUUID().toString(), 409);
    long refund =
        money(b, "REFUND", "100", payment, UUID.randomUUID().toString(), 200).get("id").asLong();
    money(b, "REVERSAL", "100", refund, UUID.randomUUID().toString(), 200);
    money(b, "REVERSAL", "500", payment, UUID.randomUUID().toString(), 409);
    money(b, "REFUND", "501", payment, UUID.randomUUID().toString(), 409);
    var d = getAs(fin, "/projects/" + pid, 200);
    assertEquals(500.0, d.get("bills").get(0).get("net").asDouble());
  }

  @Test
  void anonymousAndCsrfDenied() throws Exception {
    mvc.perform(get("/api/projects"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isUnauthorized());
    mvc.perform(post("/api/projects").session(admin).contentType("application/json").content("{}"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isForbidden());
  }

  @Test
  void adminPasswordNotReturnedAndLastAdminProtected() throws Exception {
    var accounts = getAs(admin, "/admin/users", 200);
    assertNull(accounts.get(0).get("passwordHash"));
    mvc.perform(delete("/api/admin/users/1").session(admin).with(csrf()))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isConflict());
  }

  @Test
  void rejectionFreesPendingQuantity() throws Exception {
    long c = claim("10");
    act(manager, "/claims/" + c + "/review", m("approve", false, "reviewNote", "TEST rework"), 200);
    long next = claim("10");
    approve(manager, "claims", next);
    assertEquals(
        10.0,
        getAs(manager, "/projects/" + pid, 200).get("works").get(0).get("accepted").asDouble());
  }

  @Test
  void departmentScopeDenied() throws Exception {
    long d =
        postAs(admin, "/admin/departments", m("name", "TEST foreign " + suffix), 200)
            .get("id")
            .asLong();
    long role = getAs(admin, "/admin/roles", 200).get(1).get("id").asLong();
    user("foreign" + suffix, role, d);
    getAs(login("foreign" + suffix), "/projects/" + pid, 403);
  }

  @Test
  void memberRemovalImmediatelyRevokesSite() throws Exception {
    act(manager, "/members", m("accountId", siteId, "remove", true), 200);
    getAs(site, "/projects/" + pid, 403);
  }

  @Test
  void attachmentContentAndScope() throws Exception {
    long cid = claim("1");
    byte[] bytes;
    var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", out);
    bytes = out.toByteArray();
    var f = new MockMultipartFile("file", "TEST.png", "image/png", bytes);
    var res =
        mvc.perform(
                multipart("/api/projects/" + pid + "/claims/" + cid + "/attachments")
                    .file(f)
                    .session(site)
                    .with(csrf()))
            .andReturn();
    assertEquals(200, res.getResponse().getStatus());
    long id = json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    mvc.perform(get("/api/attachments/" + id).session(site))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    mvc.perform(get("/api/attachments/" + id).session(other))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isForbidden());
    approve(manager, "claims", cid);
    mvc.perform(
            multipart("/api/projects/" + pid + "/claims/" + cid + "/attachments")
                .file(f)
                .session(site)
                .with(csrf()))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isForbidden());
  }

  @Test
  void approvedCostCorrectionRequiresOriginalAndIndependentReview() throws Exception {
    long original =
        act(
                manager,
                "/costs",
                m(
                    "workId",
                    wid,
                    "category",
                    "MATERIAL",
                    "amount",
                    "100",
                    "reference",
                    "TEST receipt",
                    "note",
                    "TEST cost"),
                200)
            .get("id")
            .asLong();
    approve(fin, "costs", original);
    long adjust =
        act(
                manager,
                "/costs",
                m(
                    "workId",
                    wid,
                    "category",
                    "MATERIAL",
                    "amount",
                    "-30",
                    "originalId",
                    original,
                    "reference",
                    "TEST refund receipt",
                    "note",
                    "TEST correction"),
                200)
            .get("id")
            .asLong();
    approve(fin, "costs", adjust);
    act(
        manager,
        "/costs",
        m(
            "workId",
            wid,
            "category",
            "MATERIAL",
            "amount",
            "-71",
            "originalId",
            original,
            "reference",
            "TEST overrefund",
            "note",
            "TEST"),
        409);
    assertEquals(
        70.0, getAs(fin, "/projects/" + pid, 200).get("summary").get("actualCost").asDouble());
  }

  @Test
  void draftContractCanEditAndDeleteOnlyWithoutReferences() throws Exception {
    long draft =
        postAs(
                manager,
                "/projects",
                m(
                    "code",
                    "TEST-draft-" + suffix,
                    "name",
                    "TEST draft",
                    "customer",
                    "TEST customer",
                    "site",
                    "TEST site",
                    "departmentId",
                    1,
                    "managerId",
                    managerId,
                    "startDate",
                    today().toString(),
                    "endDate",
                    today().plusDays(10).toString()),
                200)
            .get("id")
            .asLong();
    var d = getAs(manager, "/projects/" + draft, 200).get("project");
    var body =
        m(
            "revision",
            d.get("revision").asLong(),
            "code",
            "TEST-new-" + suffix,
            "name",
            "TEST renamed",
            "customer",
            "TEST customer",
            "site",
            "TEST new site",
            "startDate",
            today().toString(),
            "endDate",
            today().plusDays(12).toString());
    var result =
        mvc.perform(
                put("/api/projects/" + draft)
                    .session(manager)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    long version =
        json.readTree(result.getResponse().getContentAsString()).get("revision").asLong();
    mvc.perform(
            delete("/api/projects/" + draft)
                .session(manager)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(m("revision", version))))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    getAs(manager, "/projects/" + draft, 404);
  }

  @Test
  void passwordResetRevokesExistingSession() throws Exception {
    var a = getAs(admin, "/admin/users", 200);
    JsonNode worker = null;
    for (var row : a) if (row.get("id").asLong() == siteId) worker = row;
    var data =
        m(
            "username",
            worker.get("username").asString(),
            "displayName",
            worker.get("displayName").asString(),
            "departmentId",
            1,
            "roleId",
            worker.get("roleId").asLong(),
            "enabled",
            true,
            "password",
            "NewAa9" + UUID.randomUUID());
    mvc.perform(
            put("/api/admin/users/" + siteId)
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(data)))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    getAs(site, "/projects", 401);
  }

  @Test
  void simultaneousClaimsCannotExceedCapacity() throws Exception {
    long version = getAs(manager, "/projects/" + pid, 200).get("project").get("revision").asLong();
    var body =
        json.writeValueAsString(
            m(
                "revision",
                version,
                "workId",
                wid,
                "quantity",
                "7",
                "note",
                "TEST concurrent",
                "workDate",
                today().toString()));
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var results =
          pool.invokeAll(
              java.util.List.of(
                  () ->
                      mvc.perform(
                              post("/api/projects/" + pid + "/claims")
                                  .session(site)
                                  .with(csrf())
                                  .contentType("application/json")
                                  .content(body))
                          .andReturn()
                          .getResponse()
                          .getStatus(),
                  () ->
                      mvc.perform(
                              post("/api/projects/" + pid + "/claims")
                                  .session(site)
                                  .with(csrf())
                                  .contentType("application/json")
                                  .content(body))
                          .andReturn()
                          .getResponse()
                          .getStatus()));
      var codes = new java.util.ArrayList<Integer>();
      for (var f : results) codes.add((Integer) f.get());
      java.util.Collections.sort(codes);
      assertEquals(java.util.List.of(200, 409), codes);
    }
  }
}

// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.buildflow;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 工程量、金额精度和结算边界的单元测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class BuildPolicyTest {
  @Test
  void noFloatCurrency() {
    assertEquals(
        new BigDecimal("10.01"),
        BuildPolicy.amount(new BigDecimal("3.3333"), new BigDecimal("3.002")));
  }

  @Test
  void preciseQuantities() {
    assertEquals(new BigDecimal("0.1234"), BuildPolicy.decimal("0.1234", 4, true));
  }

  @Test
  void rejectPrecisionLoss() {
    assertThrows(ArithmeticException.class, () -> BuildPolicy.decimal("1.001", 2, true));
  }

  @Test
  void rejectHugeInputs() {
    assertThrows(Problem.class, () -> BuildPolicy.decimal("1".repeat(50), 2, false));
  }

  @Test
  void positiveClaims() {
    assertThrows(Problem.class, () -> BuildPolicy.decimal("0", 4, true));
    assertThrows(Problem.class, () -> BuildPolicy.decimal("-1", 4, true));
  }

  @Test
  void signedChanges() {
    assertEquals(new BigDecimal("-1.0000"), BuildPolicy.decimal("-1", 4, false));
  }

  @Test
  void overClaim() {
    assertThrows(Problem.class, () -> BuildPolicy.capacity(new BigDecimal("2"), BigDecimal.ONE));
  }

  @Test
  void retentionIsRounded() {
    assertEquals(
        new BigDecimal("5.00"),
        BuildPolicy.retention(new BigDecimal("100.01"), new BigDecimal("5")));
  }

  @Test
  void invalidRetention() {
    assertThrows(Problem.class, () -> BuildPolicy.retention(BigDecimal.TEN, new BigDecimal("101")));
  }
}

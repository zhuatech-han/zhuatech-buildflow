// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.buildflow;

import java.math.*;

/** 工程量、单价、保留款与金额边界，金额统一两位小数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class BuildPolicy {
  private BuildPolicy() {}

  /** 拒绝额外精度与异常金额；允许有符号增减项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal decimal(Object value, int scale, boolean positive) {
    if (value == null || value.toString().length() > 40) throw new Problem(400, "INVALID_AMOUNT");
    BigDecimal n = new BigDecimal(value.toString()).setScale(scale, RoundingMode.UNNECESSARY);
    if (n.abs().compareTo(new BigDecimal("1000000000")) > 0 || positive && n.signum() <= 0)
      throw new Problem(400, "INVALID_AMOUNT");
    return n;
  }

  /** 每行按合同冻结单价计算并四舍五入，不使用浮点货币。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal amount(BigDecimal quantity, BigDecimal price) {
    return quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
  }

  /** 验证累计工程量不超过已批准合同量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void capacity(BigDecimal total, BigDecimal limit) {
    if (total.compareTo(limit) > 0 || total.signum() < 0)
      throw new Problem(409, "QUANTITY_EXCEEDED");
  }

  /** 申请冻结保留款，比例仅由合同录入，不视为法规预设。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal retention(BigDecimal gross, BigDecimal percent) {
    if (percent.signum() < 0 || percent.compareTo(new BigDecimal("100")) > 0)
      throw new Problem(400, "INVALID_RETENTION");
    return gross.multiply(percent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
  }
}

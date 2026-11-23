package tw.jizhang.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 從付款通知文字解析金額、收支與店家。純 Java，可在電腦上單元測試。 */
public final class Parser {

    public static final class Result {
        public final double amount;
        public final String type;      // "out" 或 "in"
        public final String merchant;  // 可能是空字串
        Result(double amount, String type, String merchant) {
            this.amount = amount; this.type = type; this.merchant = merchant;
        }
    }

    private static final Pattern SKIP = Pattern.compile("驗證碼|OTP|動態密碼|一次性密碼|認證碼|密碼函|未成功|失敗|取消交易|交易取消|預授權取消");
    private static final Pattern OUT = Pattern.compile("消費|刷卡|付款|支付|扣款|交易成功|已付|繳費|購買|扣繳|交易通知|簽帳");
    private static final Pattern IN = Pattern.compile("入帳|轉入|收款|已收到|退款|退貨|存入|匯入");
    private static final Pattern STRONG_OUT = Pattern.compile("消費|刷卡|扣款|付款成功|支付成功");

    // 去掉餘額、回饋、點數這類不是本次金額的片段
    private static final Pattern NOISE = Pattern.compile(
            "(可用|帳戶|錢包|卡片)?餘額[^，,。；;\\n]*|(回饋|點數|紅利|LINE ?POINTS|現金回饋|累積)[^，,。；;\\n]*|額度[^，,。；;\\n]*");

    private static final Pattern AMT_PREFIX = Pattern.compile(
            "(?:NT\\$|NTD|TWD|新臺幣|新台幣|臺幣|台幣|＄|\\$)\\s*([0-9][0-9,，]*(?:\\.[0-9]{1,2})?)");
    private static final Pattern AMT_LABEL = Pattern.compile(
            "(?:金額|消費|扣款|付款|支付|交易)[:：為\\s]*(?:NT\\$|\\$)?\\s*([0-9][0-9,，]*(?:\\.[0-9]{1,2})?)");
    private static final Pattern AMT_SUFFIX = Pattern.compile(
            "([0-9][0-9,，]*(?:\\.[0-9]{1,2})?)\\s*(?:元|塊)");

    private static final Pattern[] MERCHANT = {
            Pattern.compile("(?:商店|店家|特店|商家|收款方|商戶)(?:名稱)?[:：]\\s*([^\\s，,。；;\\n]{1,24})"),
            Pattern.compile("付款給\\s*([^\\s，,。；;\\n]{1,24})"),
            Pattern.compile("於\\s*([^\\s，,。；;\\n0-9/:]{2,24}?)\\s*(?:消費|刷卡|付款|支付|交易|扣款)"),
            Pattern.compile("在\\s*([^\\s，,。；;\\n0-9/:]{2,24}?)\\s*(?:消費|刷卡|付款|支付|交易|完成)"),
            Pattern.compile("(?:向|至)\\s*([^\\s，,。；;\\n0-9/:]{2,24}?)\\s*(?:付款|支付|消費)"),
    };

    private Parser() {}

    public static Result parse(String text) {
        if (text == null) return null;
        String t = text.replace(' ', ' ').trim();
        if (t.isEmpty() || SKIP.matcher(t).find()) return null;
        boolean out = OUT.matcher(t).find();
        boolean in = IN.matcher(t).find();
        if (!out && !in) return null;

        String clean = NOISE.matcher(t).replaceAll(" ");
        double amt = firstAmount(AMT_PREFIX, clean);
        if (amt <= 0) amt = firstAmount(AMT_LABEL, clean);
        if (amt <= 0) amt = firstAmount(AMT_SUFFIX, clean);
        if (amt <= 0 || amt >= 10_000_000) return null;

        String type = (in && !STRONG_OUT.matcher(t).find()) ? "in" : "out";
        if (in && !out) type = "in";

        String merchant = "";
        for (Pattern p : MERCHANT) {
            Matcher m = p.matcher(clean);
            if (m.find()) {
                String s = m.group(1).replaceAll("^[「『(（\\[]+|[」』)）\\]：:]+$", "").trim();
                if (s.length() >= 1 && !s.matches(".*(您|你的|帳戶|信用卡|金融卡|本行).*")) { merchant = s; break; }
            }
        }
        return new Result(amt, type, merchant);
    }

    private static double firstAmount(Pattern p, String s) {
        Matcher m = p.matcher(s);
        while (m.find()) {
            String num = m.group(1).replace(",", "").replace("，", "");
            try {
                double v = Double.parseDouble(num);
                if (v > 0) return v;
            } catch (NumberFormatException ignored) { }
        }
        return 0;
    }
}

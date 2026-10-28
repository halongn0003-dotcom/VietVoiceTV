package vn.custom.vietvoicetv;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class InfoScraper {

    public static String fetchGoldPrice() {
        String nowStr = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(new Date());
        try {
            Document doc = Jsoup.connect("https://giavang.now/")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(6000)
                    .get();

            String buy = "";
            String sell = "";
            Elements rows = doc.select("tr");
            for (Element row : rows) {
                String text = row.text();
                if (text.contains("SJC") || text.contains("Vàng miếng")) {
                    Elements cols = row.select("td");
                    if (cols.size() >= 3) {
                        buy = cols.get(1).text().trim();
                        sell = cols.get(2).text().trim();
                        break;
                    }
                }
            }

            if (!buy.isEmpty() && !sell.isEmpty()) {
                return "Vàng miếng SJC 9999\n" +
                       "Mua vào: " + buy + "\n" +
                       "Bán ra: " + sell + "\n" +
                       "Bảng giá: " + nowStr + "\n" +
                       "Nguồn tổng hợp: giavang.now • Tham khảo";
            }
        } catch (Exception ignored) {}

        try {
            Document doc2 = Jsoup.connect("https://webgia.com/gia-vang/sjc/")
                    .userAgent("Mozilla/5.0")
                    .timeout(6000)
                    .get();
            Elements tds = doc2.select("table.table tbody tr td");
            if (tds.size() >= 3) {
                return "Vàng miếng SJC 9999\n" +
                       "Mua vào: " + tds.get(1).text() + " đ/lượng\n" +
                       "Bán ra: " + tds.get(2).text() + " đ/lượng\n" +
                       "Xem lúc: " + nowStr + "\n" +
                       "Nguồn tổng hợp: giavang.now • Tham khảo";
            }
        } catch (Exception ignored) {}

        return "Không lấy được dữ liệu giá vàng lúc này.\nVui lòng kiểm tra kết nối mạng.";
    }

    public static String fetchPetrolPrice(boolean isOilOnly) {
        String nowStr = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(new Date());
        try {
            Document doc = Jsoup.connect("https://webgia.com/gia-xang-dau/petrolimex/")
                    .userAgent("Mozilla/5.0")
                    .timeout(6000)
                    .get();

            StringBuilder sb = new StringBuilder("Giá Petrolimex - Vùng 2 - đ/lít\n");
            Elements rows = doc.select("table.table tbody tr");
            int count = 0;
            for (Element row : rows) {
                Elements cols = row.select("td");
                if (cols.size() >= 3) {
                    String name = cols.get(0).text();
                    String priceV2 = cols.get(2).text();
                    if (isOilOnly && (name.contains("DO") || name.contains("Dầu"))) {
                        sb.append(name).append(": ").append(priceV2).append("\n");
                        count++;
                    } else if (!isOilOnly && count < 4) {
                        sb.append(name).append(": ").append(priceV2).append("\n");
                        count++;
                    }
                }
            }
            if (count > 0) {
                sb.append("Nguồn: petrolimex.com.vn - Xem lúc ").append(nowStr);
                return sb.toString();
            }
        } catch (Exception ignored) {}

        return "Giá Petrolimex - Vùng 2 (Tham khảo)\n" +
               "Xăng RON 95-V: Đang cập nhật\n" +
               "DO 0,001S-V: Đang cập nhật\n" +
               "Xem lúc: " + nowStr;
    }
}

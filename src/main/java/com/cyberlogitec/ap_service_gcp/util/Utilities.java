package com.cyberlogitec.ap_service_gcp.util;

import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class Utilities {
    public <T> T retry(ApiAction<T> action, int maxRetries) throws IOException {
        int attempt = 0;
        long waitTime = 3000;

        while (true) {
            try {
                return action.execute();
            } catch (IOException e) {
                attempt++;
                if (attempt > maxRetries) {
                    System.err.println("retried " + maxRetries + " but failed to retry.");
                    throw e;
                }

                System.out.println("Error occurs: " + e.getMessage() + ". trying " + attempt + "...");

                try {
                    TimeUnit.MILLISECONDS.sleep(waitTime);
                    waitTime *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Timeout when trying", ie);
                }
            }
        }
    }

    public String calculateSubRangeA1(String baseRangeA1, int startRowIndex, int endRowIndex) {
        String regex = "^(?:'([^']+)'!)?(\\$?[A-Z]+)(\\$?\\d+)(?::(\\$?[A-Z]+)(\\$?\\d+)?)?$";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher match = pattern.matcher(baseRangeA1);

        if (!match.find()) {
            throw new IllegalArgumentException("Invalid base range: " + baseRangeA1);
        }

        String sheetName = match.group(1);
        String startCol = match.group(2);
        String baseStartRowRaw = match.group(3);
        String endCol = match.group(4) != null ? match.group(4) : startCol;

        int numRows = endRowIndex - startRowIndex;
        if (numRows <= 0) return null;

        int baseStartRow = Integer.parseInt(baseStartRowRaw.replace("$", ""));
        boolean rowIsAbsolute = baseStartRowRaw.startsWith("$");
        String absPrefix = rowIsAbsolute ? "$" : "";

        int newStartRow = baseStartRow + startRowIndex;
        int newEndRow = newStartRow + numRows - 1;

        String sheetPrefix = (sheetName != null) ? "'" + sheetName + "'!" : "";
        return sheetPrefix + startCol + absPrefix + newStartRow + ":" + endCol + absPrefix + newEndRow;
    }

    public void logMemory(String stage) {
        Runtime runtime = Runtime.getRuntime();
        long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMem = runtime.maxMemory() / (1024 * 1024);

        // In ra console (Cloud Run Logs sẽ bắt được)
        System.out.println("📊 MEMORY [" + stage + "]: Đang dùng " + usedMem + "MB / Tối đa JVM được cấp " + maxMem + "MB");
    }

    public List<String> flattenList(List<List<Object>> raw) {
        List<String> result = new ArrayList<>();
        if (raw != null) {
            for (List<Object> row : raw) {
                for (Object item : row) {
                    if (item != null && !item.toString().isEmpty()) {
                        result.add(item.toString());
                    }
                }
            }
        }
        return result;
    }

    public static List<List<Object>> transposeList(List<List<Object>> table) {
        if (table == null || table.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. Tìm số cột lớn nhất (để xử lý trường hợp các dòng dài ngắn khác nhau)
        int maxCols = 0;
        for (List<Object> row : table) {
            if (row != null && row.size() > maxCols) {
                maxCols = row.size();
            }
        }

        int rows = table.size();
        List<List<Object>> transposed = new ArrayList<>();

        // 2. Khởi tạo cấu trúc cho ma trận mới (maxCols dòng)
        for (int i = 0; i < maxCols; i++) {
            transposed.add(new ArrayList<>(rows));
        }

        // 3. Xoay dữ liệu
        for (int i = 0; i < maxCols; i++) { // Duyệt theo cột của bảng gốc
            for (int j = 0; j < rows; j++) { // Duyệt theo hàng của bảng gốc
                List<Object> originalRow = table.get(j);

                // Lấy giá trị (kiểm tra null safe vì dòng có thể ngắn hơn maxCols)
                Object value = null;
                if (originalRow != null && i < originalRow.size()) {
                    value = originalRow.get(i);
                }

                // Thêm vào bảng mới
                transposed.get(i).add(value);
            }
        }

        return transposed;
    }

    public static List<List<Object>> transposeList(List<List<Object>> table, boolean removeSourceHeader) {
        // 1. Kiểm tra null hoặc rỗng
        if (table == null || table.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. Xử lý loại bỏ Header
        List<List<Object>> dataToProcess;
        if (removeSourceHeader) {
            // Nếu chỉ có 1 dòng (là header) mà đòi xóa -> trả về rỗng
            if (table.size() <= 1) {
                return new ArrayList<>();
            }
            // Cắt từ dòng 1 trở đi (bỏ dòng 0)
            dataToProcess = table.subList(1, table.size());
        } else {
            // Giữ nguyên toàn bộ
            dataToProcess = table;
        }

        // 3. Tìm số cột lớn nhất (để xử lý mảng lởm chởm - Jagged Array)
        int maxCols = 0;
        for (List<Object> row : dataToProcess) {
            if (row != null && row.size() > maxCols) {
                maxCols = row.size();
            }
        }

        // 4. Khởi tạo mảng kết quả
        List<List<Object>> transposed = new ArrayList<>();
        for (int i = 0; i < maxCols; i++) {
            transposed.add(new ArrayList<>());
        }

        // 5. Xoay dữ liệu
        for (int colIndex = 0; colIndex < maxCols; colIndex++) { // Duyệt theo cột của bảng gốc
            for (int rowIndex = 0; rowIndex < dataToProcess.size(); rowIndex++) { // Duyệt theo hàng
                List<Object> row = dataToProcess.get(rowIndex);

                // Lấy giá trị an toàn (tránh lỗi IndexOutOfBounds nếu dòng ngắn)
                Object value = null;
                if (row != null && colIndex < row.size()) {
                    value = row.get(colIndex);
                }

                transposed.get(colIndex).add(value);
            }
        }

        return transposed;
    }

    public String nameTargetWeek(List<List<Object>> targetWeekFull) {
        List<Integer> nums = new ArrayList<>();
        for (List<Object> row : targetWeekFull) {
            for (Object cell : row) {
                try {
                    if (cell != null && !cell.toString().isEmpty())
                        nums.add(Integer.parseInt(cell.toString()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (nums.isEmpty()) return "";
        int min = Collections.min(nums);
        int max = Collections.max(nums);
        return "W" + (min % 100) + "-W" + (max % 100);
    }
}

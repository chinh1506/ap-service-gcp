package com.cyberlogitec.ap_service_gcp.util;

import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class Utilities {
    public  <T> T retry(ApiAction<T> action, int maxRetries) throws IOException {
        int attempt = 0;
        long waitTime = 1000;

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
}

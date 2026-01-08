package com.cyberlogitec.ap_service_gcp.util;

import lombok.AllArgsConstructor;
import lombok.ToString;

public class TaskPartitioner {

//    public static void main(String[] args) {
//        // --- GIẢ LẬP MÔI TRƯỜNG CLOUD RUN ---
//        // Trong thực tế, bạn lấy các giá trị này từ biến môi trường (System.getenv)
//        int totalItems = 10;   // Tổng số lượng file/item cần xử lý
//        int totalTasks = 18;    // Tổng số task chạy song song
//        int taskIndex = 9;     // Task hiện tại (Ví dụ đây là task số 10)
//
//        // --- TÍNH TOÁN ---
//        Partition myPartition = calculatePartition(totalItems, totalTasks, taskIndex);
//        System.out.println(myPartition);
//
//        // --- KẾT QUẢ ---
//        if (myPartition.size > 0) {
//            System.out.printf("Task %d xử lý %d items: Từ index %d đến %d%n",
//                    taskIndex, myPartition.size, myPartition.start, myPartition.end);
//
//            // Ví dụ vòng lặp xử lý thực tế
//            // for (int i = myPartition.start; i <= myPartition.end; i++) { ... }
//        } else {
//            System.out.printf("Task %d: Không có item nào được phân (Do số lượng task > số item).%n", taskIndex);
//        }
//    }

    // Class chứa kết quả trả về
    @ToString
    @AllArgsConstructor
    public static class Partition {
        public int start;
        public int end;
        public int size;
    }

    public static Partition calculatePartition(int totalItems, int totalTasks, int taskIndex) {
        int baseSize = totalItems / totalTasks;     // Ví dụ: 100 / 18 = 5
        int remainder = totalItems % totalTasks;    // Ví dụ: 100 % 18 = 10 (10 task đầu sẽ gánh thêm 1)
        // 2. Tính số lượng item của riêng task này
        // Nếu index < số dư -> task thuộc nhóm "nhà giàu" (được +1 item)
        int mySize = baseSize + (taskIndex < remainder ? 1 : 0);

        if (mySize == 0) {
            return new Partition(-1, -1, 0);
        }

        // 3. Tính vị trí bắt đầu (Start Offset)
        int myStart;
        if (taskIndex < remainder) {
            // Nhóm đầu: Mỗi task trước đó đều có (baseSize + 1) item
            myStart = taskIndex * (baseSize + 1);
        } else {
            // Nhóm sau:
            // = (Tổng item của toàn bộ nhóm đầu) + (Tổng item của các task nhóm sau đứng trước mình)
            myStart = (remainder * (baseSize + 1)) + ((taskIndex - remainder) * baseSize);
        }

        // 4. Tính vị trí kết thúc
        int myEnd = myStart + mySize - 1;

        return new Partition(myStart, myEnd, mySize);
    }
}

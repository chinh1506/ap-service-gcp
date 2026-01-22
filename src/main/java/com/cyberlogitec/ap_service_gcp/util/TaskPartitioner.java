package com.cyberlogitec.ap_service_gcp.util;

import lombok.AllArgsConstructor;
import lombok.ToString;

public class TaskPartitioner {

    public static void main(String[] args) {
        int totalItems = 100;
        int totalTasks = 10;
        int taskIndex = 1;
        Partition myPartition = calculatePartition(totalItems, totalTasks, taskIndex);
        System.out.println(myPartition);

        if (myPartition.size > 0) {
            System.out.printf("Task %d xử lý %d items: Từ index %d đến %d%n",
                    taskIndex, myPartition.size, myPartition.start, myPartition.end);
        } else {
            System.out.printf("Task %d: Không có item nào được phân (Do số lượng task > số item).%n", taskIndex);
        }
    }

    @ToString
    @AllArgsConstructor
    public static class Partition {
        public int start;
        public int end;
        public int size;
    }

    public static Partition calculatePartition(int totalItems, int totalTasks, int taskIndex) {
        int baseSize = totalItems / totalTasks;
        int remainder = totalItems % totalTasks;
        int mySize = baseSize + (taskIndex < remainder ? 1 : 0);

        if (mySize == 0) {
            return new Partition(-1, -1, 0);
        }

        int myStart;
        if (taskIndex < remainder) {
            myStart = taskIndex * (baseSize + 1);
        } else {
            myStart = (remainder * (baseSize + 1)) + ((taskIndex - remainder) * baseSize);
        }
        int myEnd = myStart + mySize - 1;

        return new Partition(myStart, myEnd, mySize);
    }
}

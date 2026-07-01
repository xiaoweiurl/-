package com.imagemanager.config;

import com.imagemanager.service.OpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;

/**
 * 性能快照定时采集
 * 每 30 秒自动采集 JVM 和系统指标，写入 performance_snapshots 表
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class PerformanceSnapshotScheduler {

    private final OpsService opsService;

    @Scheduled(fixedRate = 30000, initialDelay = 10000)
    public void recordSnapshot() {
        try {
            Map<String, Object> snapshot = new HashMap<>();

            // CPU 使用率
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            double cpuUsage = 0;
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                cpuUsage = sunOsBean.getProcessCpuLoad() * 100;
                if (cpuUsage < 0) cpuUsage = 0;
            }
            snapshot.put("cpuUsage", Math.round(cpuUsage * 100.0) / 100.0);

            // JVM 内存
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
            long heapMax = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
            double memoryUsagePct = heapMax > 0 ? (heapUsed * 100.0 / heapMax) : 0;

            snapshot.put("memoryUsedMb", heapUsed);
            snapshot.put("memoryTotalMb", heapMax);
            snapshot.put("memoryUsagePct", Math.round(memoryUsagePct * 100.0) / 100.0);
            snapshot.put("jvmHeapUsedMb", heapUsed);
            snapshot.put("jvmHeapMaxMb", heapMax);

            // 线程数
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            snapshot.put("jvmThreadCount", threadBean.getThreadCount());

            // GC 次数
            long gcCount = 0;
            for (var gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcCount += gcBean.getCollectionCount();
            }
            snapshot.put("jvmGcCount", gcCount);

            // 磁盘使用（简化，取当前目录）
            java.io.File root = new java.io.File("/");
            long diskTotal = root.getTotalSpace() / (1024 * 1024);
            long diskFree = root.getUsableSpace() / (1024 * 1024);
            long diskUsed = diskTotal - diskFree;
            double diskUsagePct = diskTotal > 0 ? (diskUsed * 100.0 / diskTotal) : 0;

            snapshot.put("diskUsedMb", diskUsed);
            snapshot.put("diskTotalMb", diskTotal);
            snapshot.put("diskUsagePct", Math.round(diskUsagePct * 100.0) / 100.0);

            opsService.recordPerformanceSnapshot(snapshot);
        } catch (Exception e) {
            log.warn("[PerfSnapshot] 采集失败: {}", e.getMessage());
        }
    }
}

package com.opsmind.backend.diagnosis.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.opsmind.backend.diagnosis.model.DiagnosisTask;
import com.opsmind.backend.diagnosis.model.DiagnosisTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/** 异步诊断任务的基础保存和查询入口。 */
public interface DiagnosisTaskRepository extends JpaRepository<DiagnosisTask, String> {

    /** 查询服务启动前遗留的未完成任务，用于单实例启动对账。 */
    List<DiagnosisTask> findAllByStatusIn(Collection<DiagnosisTaskStatus> statuses);

    /** 查询某个故障最近创建的诊断任务，用于前端刷新后恢复任务上下文。 */
    Optional<DiagnosisTask> findFirstByIncidentIdOrderByCreatedAtDesc(String incidentId);

    /**
     * Redis 去重锁已被其他请求持有时，从 MySQL 查找可复用的进行中任务。
     */
    Optional<DiagnosisTask> findFirstByIncidentIdAndStatusInOrderByCreatedAtDesc(
            String incidentId,
            Collection<DiagnosisTaskStatus> statuses
    );
}

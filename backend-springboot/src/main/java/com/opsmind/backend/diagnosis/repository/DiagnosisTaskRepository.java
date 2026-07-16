package com.opsmind.backend.diagnosis.repository;

import com.opsmind.backend.diagnosis.model.DiagnosisTask;
import org.springframework.data.jpa.repository.JpaRepository;

/** 异步诊断任务的基础保存和查询入口。 */
public interface DiagnosisTaskRepository extends JpaRepository<DiagnosisTask, String> {
}

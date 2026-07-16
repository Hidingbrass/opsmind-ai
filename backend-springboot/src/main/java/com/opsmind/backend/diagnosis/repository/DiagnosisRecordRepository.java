package com.opsmind.backend.diagnosis.repository;

import java.util.List;

import com.opsmind.backend.diagnosis.model.DiagnosisRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/** 诊断报告持久化仓库。 */
public interface DiagnosisRecordRepository extends JpaRepository<DiagnosisRecord, String> {

    /**
     * Spring Data 根据方法名自动生成“按故障筛选并按时间倒序”的 SQL。
     *
     * @param incidentId 故障事件 id
     * @return 最新报告在前的诊断历史
     */
    List<DiagnosisRecord> findByIncidentIdOrderByCreatedAtDesc(String incidentId);
}

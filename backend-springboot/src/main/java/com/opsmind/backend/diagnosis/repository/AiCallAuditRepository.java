package com.opsmind.backend.diagnosis.repository;

import java.util.List;

import com.opsmind.backend.diagnosis.model.AiCallAudit;
import org.springframework.data.jpa.repository.JpaRepository;

/** AI 服务调用审计的持久化和按任务查询入口。 */
public interface AiCallAuditRepository extends JpaRepository<AiCallAudit, String> {

    List<AiCallAudit> findByTaskIdOrderByCreatedAtDesc(String taskId);
}

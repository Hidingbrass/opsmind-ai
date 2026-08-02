package com.opsmind.backend.tool.repository;

import java.util.List;

import com.opsmind.backend.tool.model.ToolCallAudit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 工具调用审计记录的数据库访问入口。
 *
 * <p>继承 JpaRepository 后，Spring Data 会自动提供 save、findById 等基础方法。
 */
public interface ToolCallAuditRepository extends JpaRepository<ToolCallAudit, String> {

    /**
     * 按诊断任务查询工具调用历史，并让最新的调用排在前面。
     *
     * <p>Spring Data 会根据方法名自动生成查询，不需要手写 SQL。
     *
     * @param taskId 异步诊断任务 id
     * @return 该任务的工具调用审计记录
     */
    List<ToolCallAudit> findByTaskIdOrderByCreatedAtDesc(String taskId);
}

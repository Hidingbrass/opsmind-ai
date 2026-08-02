package com.opsmind.backend.incident.repository;

import com.opsmind.backend.incident.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 故障事件持久化入口。
 *
 * <p>继承 JpaRepository 后由 Spring Data 自动提供保存、按 id 查询、列表和删除等基础操作。
 */
public interface IncidentRepository extends JpaRepository<Incident, String> {
}

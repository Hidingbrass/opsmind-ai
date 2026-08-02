#!/bin/sh
set -eu

# Chroma 可能比 AI 容器更晚就绪。先重试知识库导入，避免一次连接失败让整套编排退出。
attempt=1
until python -m scripts.ingest_runbooks; do
    if [ "$attempt" -ge 20 ]; then
        echo "Runbook 导入连续失败，AI 服务停止启动" >&2
        exit 1
    fi
    echo "Chroma 尚未就绪，3 秒后重试 Runbook 导入（${attempt}/20）" >&2
    attempt=$((attempt + 1))
    sleep 3
done

exec uvicorn app.main:app --host 0.0.0.0 --port 8000

FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HF_HOME=/models/huggingface

WORKDIR /app
RUN useradd --system --uid 10001 --create-home opsmind
COPY requirements.txt .
# 诊断只做 CPU embedding 推理。先安装官方 CPU wheel，避免默认 PyPI
# 在 Linux/ARM 上解析出数 GB 的 CUDA 运行库。
RUN pip install --no-cache-dir \
        --index-url https://download.pytorch.org/whl/cpu \
        "torch==2.12.0+cpu" \
    && pip install --no-cache-dir -r requirements.txt
COPY app app
COPY scripts scripts
COPY knowledge knowledge
COPY docker-entrypoint.sh /usr/local/bin/opsmind-ai-entrypoint
RUN chmod +x /usr/local/bin/opsmind-ai-entrypoint \
    && mkdir -p /models/huggingface \
    && chown -R opsmind:opsmind /app /models

USER opsmind
EXPOSE 8000
ENTRYPOINT ["opsmind-ai-entrypoint"]

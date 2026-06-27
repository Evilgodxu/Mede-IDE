package com.medeide.jh.core.data.source.local

import com.medeide.jh.model.chat.RecommendedModel

object RecommendedModels {

    val RECOMMENDED_MODELS = listOf(
        RecommendedModel(
            name = "gemma-4-E2B-IT-多模态",
            size = "~2.6 GB",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
            description = "Gemma4 多模态，支持图像理解，6GB+ RAM",
            fileName = "gemma-4-E2B-it.litertlm"
        ),
        RecommendedModel(
            name = "gemma-4-E4B-IT-多模态",
            size = "~5 GB",
            url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true",
            description = "Gemma4 多模态，支持图像理解，8GB+ RAM",
            fileName = "gemma-4-E4B-it.litertlm"
        ),
        RecommendedModel(
            name = "gemma-3n-E2B-it (int4)",
            size = "~1.5 GB",
            url = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.litertlm?download=true",
            description = "Gemma 3n E2B 量化版，轻量级多模态，4GB+ RAM",
            fileName = "gemma-3n-E2B-it-int4.litertlm"
        ),
        RecommendedModel(
            name = "gemma-3n-E4B-it (int4)",
            size = "~2.7 GB",
            url = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-3n-E4B-it-int4.litertlm?download=true",
            description = "Gemma 3n E4B 量化版，多模态，6GB+ RAM",
            fileName = "gemma-3n-E4B-it-int4.litertlm"
        ),
        RecommendedModel(
            name = "Qwen2.5-1.5B-Instruct",
            size = "~1.5 GB",
            url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct-litert-lm/resolve/main/Qwen2.5-1.5B-Instruct.litertlm?download=true",
            description = "Qwen2.5 1.5B 指令模型，4096 上下文，轻量级，4GB+ RAM",
            fileName = "Qwen2.5-1.5B-Instruct.litertlm"
        ),
        RecommendedModel(
            name = "Qwen2.5-3B-Instruct",
            size = "~3 GB",
            url = "https://huggingface.co/litert-community/Qwen2.5-3B-Instruct-litert-lm/resolve/main/Qwen2.5-3B-Instruct.litertlm?download=true",
            description = "Qwen2.5 3B 指令模型，平衡性能，6GB+ RAM",
            fileName = "Qwen2.5-3B-Instruct.litertlm"
        ),
        RecommendedModel(
            name = "Qwen3-1.7B",
            size = "~1.7 GB",
            url = "https://huggingface.co/litert-community/Qwen3-1.7B-litert-lm/resolve/main/Qwen3-1.7B.litertlm?download=true",
            description = "Qwen3 1.7B 模型，新一代轻量级，4GB+ RAM",
            fileName = "Qwen3-1.7B.litertlm"
        ),
        RecommendedModel(
            name = "Phi-3.5-mini-instruct",
            size = "~2.3 GB",
            url = "https://huggingface.co/litert-community/Phi-3.5-mini-instruct-litert-lm/resolve/main/Phi-3.5-mini-instruct.litertlm?download=true",
            description = "微软 Phi-3.5 mini 指令模型，4K 上下文，4GB+ RAM",
            fileName = "Phi-3.5-mini-instruct.litertlm"
        ),
        RecommendedModel(
            name = "Llama-3.2-1B-Instruct",
            size = "~1.2 GB",
            url = "https://huggingface.co/litert-community/Llama-3.2-1B-Instruct-litert-lm/resolve/main/Llama-3.2-1B-Instruct.litertlm?download=true",
            description = "Meta Llama 3.2 1B 指令模型，超轻量，3GB+ RAM",
            fileName = "Llama-3.2-1B-Instruct.litertlm"
        ),
        RecommendedModel(
            name = "Llama-3.2-3B-Instruct",
            size = "~3 GB",
            url = "https://huggingface.co/litert-community/Llama-3.2-3B-Instruct-litert-lm/resolve/main/Llama-3.2-3B-Instruct.litertlm?download=true",
            description = "Meta Llama 3.2 3B 指令模型，6GB+ RAM",
            fileName = "Llama-3.2-3B-Instruct.litertlm"
        ),
        RecommendedModel(
            name = "DeepSeek-R1-Distill-Qwen-1.5B",
            size = "~1.5 GB",
            url = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B-litert-lm/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B.litertlm?download=true",
            description = "DeepSeek R1 蒸馏 1.5B，推理能力强，4GB+ RAM",
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B.litertlm"
        )
    )

    // 已知 NPU 驱动库文件（按优先级排列）
    val NPU_VENDOR_LIBS = listOf(
        "libQnnHtp.so",       // 高通 QNN HTP（Hexagon）
        "libQnnCpu.so",       // 高通 QNN CPU fallback
        "libQnnGpu.so",       // 高通 QNN GPU
        "libQnnDsp.so",       // 高通 QNN DSP
        "libneuropilot.so",   // 联发科 NeuroPilot
        "libmtk_drv.so",      // 联发科驱动
        "libhiai.so",         // 华为 HiAI（Kirin NPU）
        "libhiai_ir.so",      // 华为 HiAI IR
        "libedgetpu.so",      // Google Edge TPU / 三星
        "librknnrt.so",       // Rockchip RKNN
        "libamlnpu.so",       // Amlogic NPU
        "libnpu.so",          // 通用 / Allwinner NPU
    )

    // 常见 NPU 驱动库存放目录
    val NPU_SCAN_DIRS = listOf(
        "/vendor/lib64/",
        "/vendor/lib/",
        "/vendor/lib64/egl/",
        "/system/lib64/",
        "/system/vendor/lib64/",
        "/system/lib/",
        "/vendor/firmware/",
    )
}

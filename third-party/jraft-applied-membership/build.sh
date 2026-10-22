#!/usr/bin/env bash
# 契约：固定本地源码归档 + reviewed patch，先准入，再串行执行两轮全量 Maven verify。
# 输入：JRAFT_SOURCE_ARCHIVE/JRAFT_BUILD_OUTPUT 为绝对路径；输出必须新建或为空。
# 模式：默认 capture-input 只采集和比较实际输入，不产生 accepted manifest；verify-lock 还要求已 Review 的输入锁。
# 输出：隔离源码/m2、日志、测试结果、输入摘要和 binary/sources/消费 POM；失败保留诊断但不接受制品。
# 退出：65 为非法输入/证据或比较失败，66 为缺少源码，子进程失败状态传播。
# 隔离：不获取源码、不安装/发布/切换消费者；Maven 可正常解析依赖。法律文件和 manifest 内容不得静默改写。
set -euo pipefail
recipe_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "$recipe_dir/build.py"

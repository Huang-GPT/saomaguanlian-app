#!/bin/sh
# 使用系统 gradle 的简化 wrapper；若本机无 gradle，请安装后执行:
#   gradle wrapper --gradle-version 8.5
# 再 ./gradlew assembleDebug
gradle "$@"

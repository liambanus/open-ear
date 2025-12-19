#
# SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
#
# SPDX-License-Identifier: Apache-2.0
#

# specify the cross compiler
set(GNU_MACHINE "aarch64-none-linux-gnu-")
set(CROSS_PREFIX "aarch64-none-linux-gnu-")

set(CMAKE_C_COMPILER   ${CROSS_PREFIX}gcc)
set(CMAKE_CXX_COMPILER ${CROSS_PREFIX}g++)
set(CMAKE_AR           ${CROSS_PREFIX}ar)
set(CMAKE_STRIP        ${CROSS_PREFIX}strip)
set(CMAKE_LINKER       ${CROSS_PREFIX}ld)

set(CMAKE_CROSSCOMPILING true)
set(CMAKE_SYSTEM_NAME Linux)

set(CMAKE_SYSTEM_PROCESSOR aarch64)

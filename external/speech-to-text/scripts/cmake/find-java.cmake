#
# SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
#
# SPDX-License-Identifier: Apache-2.0
#

include_guard(DIRECTORY)
find_package(Java REQUIRED
    COMPONENTS Runtime Development)
if (Java_FOUND)
    message(STATUS "Found Java version ${Java_VERSION}")
endif()
include(UseJava)

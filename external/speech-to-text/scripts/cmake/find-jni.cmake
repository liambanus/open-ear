#
# SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
#
# SPDX-License-Identifier: Apache-2.0
#
include_guard(DIRECTORY)
# If JNI libs are to be built, find JNI include directories.
find_package(JNI REQUIRED)
if (JNI_FOUND)
    message(STATUS "JNI include paths: ${JNI_INCLUDE_DIRS}")
endif()


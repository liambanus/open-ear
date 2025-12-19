#
# SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
#
# SPDX-License-Identifier: Apache-2.0
#

include_guard(GLOBAL)
include(configuration-options)

find_package(
    Python3 3.9...3.11
    COMPONENTS Interpreter
    REQUIRED)

if (NOT Python3_FOUND)
    message(FATAL_ERROR "Required version of Python3 not found!")
else()
    message(STATUS "Python3 (v${Python3_VERSION}) found: ${Python3_EXECUTABLE}")
endif()

# If the downloads directory doesn't exist, create one
if (NOT EXISTS ${DOWNLOADS_DIR})
    file(MAKE_DIRECTORY ${DOWNLOADS_DIR})
endif()

# Create a lock so other instances of CMake configuration processes hold
# here until the lock is available.
message(STATUS "Waiting to lock resource ${DOWNLOADS_DIR} "
               "Timeout: ${DOWNLOADS_LOCK_TIMEOUT} seconds.")
file(LOCK ${DOWNLOADS_DIR}
    DIRECTORY
    GUARD PROCESS
    RESULT_VARIABLE lock_return_code
    TIMEOUT ${DOWNLOADS_LOCK_TIMEOUT})

if (NOT ${lock_return_code} STREQUAL 0)
    message(FATAL_ERROR "Failed to acquire lock for dir ${DOWNLOADS_DIR}")
endif()
message(STATUS "${DOWNLOADS_DIR} locked; running downloads script...")

execute_process(
    COMMAND ${Python3_EXECUTABLE}
        ${CMAKE_CURRENT_SOURCE_DIR}/scripts/py/download_resources.py
        --requirements-file
        ${CMAKE_CURRENT_LIST_DIR}/../py/requirements.json
        --download-dir
        ${DOWNLOADS_DIR}
    RESULT_VARIABLE return_code)

# Release the lock:
message(STATUS "Releasing locked resource ${DOWNLOADS_DIR}")
file(LOCK ${DOWNLOADS_DIR} DIRECTORY RELEASE)

if (NOT return_code STREQUAL "0")
    message(FATAL_ERROR "Failed to download resources. Error code ${return_code}")
endif ()

#
# SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
#
# SPDX-License-Identifier: Apache-2.0
#

include_guard(DIRECTORY)

set(CATCH_HASH "681e7505a50887c9085539e5135794fc8f66d8e5de28eadf13a30978627b0f47"
    CACHE STRING
    "SHA256 checksum of the Catch2 header")
set(CATCH_URL https://github.com/philsquared/Catch/releases/download/v2.13.6/catch.hpp
    CACHE STRING
    "Catch testing framework URL")

if (NOT DEFINED CATCH_DIR)
    set(CATCH_DIR ${CMAKE_CURRENT_BINARY_DIR})
endif()

file(DOWNLOAD ${CATCH_URL} "${CATCH_DIR}/catch.hpp"
     STATUS status
     EXPECTED_HASH SHA256=${CATCH_HASH})

list(GET status 0 error)

if(error)
    message(FATAL_ERROR "Could not download ${CATCH_URL}")
endif()


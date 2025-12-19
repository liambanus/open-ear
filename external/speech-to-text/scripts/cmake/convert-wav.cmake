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

execute_process(
        COMMAND ${Python3_EXECUTABLE} -m pip install librosa==0.9.2
        RESULT_VARIABLE pip_result
)

if(NOT pip_result EQUAL 0)
    message(FATAL_ERROR "Failed to install Python requirements.")
endif()

message(STATUS "Converting audio from ${WHISPER_SRC_DIR}/samples/jfk.wav "
        "to ${CMAKE_CURRENT_SOURCE_DIR}/resources/audioData.csv")

execute_process(
    COMMAND ${Python3_EXECUTABLE}
        ${CMAKE_CURRENT_SOURCE_DIR}/../scripts/py/convert_wav_to_csv.py
        --wav_file_path
        ${WHISPER_SRC_DIR}/samples/jfk.wav
        --output_file_path
        ${CMAKE_CURRENT_SOURCE_DIR}/resources/audioData.csv
        --sample_rate
        16000
    RESULT_VARIABLE return_code)

if (NOT return_code STREQUAL "0")
    message(FATAL_ERROR "Failed to convert test wave file. Error code ${return_code}")
endif ()

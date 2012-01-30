cmake_minimum_required (VERSION 2.6) 
project (TME) 

SET(GENERATED_MIST_PROTOS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../output/works/mist_proto")

list(
  APPEND
  CMAKE_MODULE_PATH
  ${CMAKE_CURRENT_SOURCE_DIR}/cmake)

add_subdirectory (mist_proto) 
add_subdirectory (mistic)

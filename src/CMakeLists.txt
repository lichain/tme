cmake_minimum_required (VERSION 2.6) 
project (TME) 

list(
  APPEND
  CMAKE_MODULE_PATH
  ${CMAKE_CURRENT_SOURCE_DIR}/cmake)

add_subdirectory (proto) 
add_subdirectory (mistic)

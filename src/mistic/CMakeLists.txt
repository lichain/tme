find_package(Boost COMPONENTS program_options REQUIRED)

include_directories(
  ${Boost_INCLUDE_DIRS}
  ${TME_SOURCE_DIR}/proto
  ${TME_SOURCE_DIR})

link_directories (${TME_BINARY_DIR}/proto) 

add_executable(mist-encode mist_encode.cpp)
add_executable(mist-decode mist_decode.cpp)
add_executable(mist-source mist_source.cpp)
add_executable(mist-session mist_session.cpp)

target_link_libraries(
  mist-encode
  proto boost_program_options ${PROTOBUF_LIBRARY})
target_link_libraries(
  mist-decode
  proto boost_program_options ${PROTOBUF_LIBRARY})
target_link_libraries(
  mist-source
  proto boost_program_options ${PROTOBUF_LIBRARY})
target_link_libraries(
  mist-session
  proto boost_program_options ${PROTOBUF_LIBRARY})

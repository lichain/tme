cmake_minimum_required(VERSION 2.6)

project(MISTiC)

find_package(Boost COMPONENTS program_options REQUIRED)

add_executable(mist-encode mist_encode.cpp)
add_executable(mist-decode mist_decode.cpp)
add_executable(mist-source mist_source.cpp)
add_executable(mist-session mist_session.cpp)

add_subdirectory(mist_protos)

target_link_libraries(mist-encode mist_protos boost_program_options protobuf)
target_link_libraries(mist-decode mist_protos boost_program_options protobuf)
target_link_libraries(mist-source mist_protos boost_program_options protobuf)
target_link_libraries(mist-session mist_protos boost_program_options protobuf)


find_package(Protobuf REQUIRED)
include_directories(${PROTOBUF_INCLUDE_DIRS})

set(args 
  PROTOFILES GateTalk.proto MistMessage.proto SpnMessage.proto ZooKeeperInfo.proto
  OUTPATH ${CMAKE_CURRENT_SOURCE_DIR}
)
PROTOBUF_GENERATE_CPP(PROTO_SRCS PROTO_HDRS ${args})

add_library(proto ${PROTO_SRCS})

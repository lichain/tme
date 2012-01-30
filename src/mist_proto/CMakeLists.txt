find_package(Protobuf REQUIRED)
include_directories(${PROTOBUF_INCLUDE_DIRS})

set(args 
  PROTOFILES GateTalk.proto MistMessage.proto ZooKeeperInfo.proto
  OUTPATH ${GENERATED_MIST_PROTOS_DIR}
)
PROTOBUF_GENERATE_CPP(PROTO_SRCS PROTO_HDRS ${args})

add_library(mist_proto ${PROTO_SRCS})

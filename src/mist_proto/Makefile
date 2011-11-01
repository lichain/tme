
SRCDIR = src

proto:  
	protoc --java_out=$(SRCDIR) GateTalk.proto
	protoc --java_out=$(SRCDIR) MistMessage.proto
	protoc --java_out=$(SRCDIR) SpnMessage.proto
	protoc --java_out=$(SRCDIR) ZooKeeperInfo.proto
	protoc --java_out=$(SRCDIR) BridgeTalk.proto
	@ant

clean:  
	@ant clean

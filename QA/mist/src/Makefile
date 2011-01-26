progs=t_multi t_socket t_socket_sink t_socket_source t_multi_source t_multi_sink t_socket_sink_new t_socket_source_new t_multi_sink_new t_multi_source_new

all: $(progs)

t_multi: MistMessage.pb.o t_multi.o
	g++ -g $^ -static -lprotobuf -lpthread -o ../$@

t_socket: MistMessage.pb.o GateTalk.pb.o t_socket.o
	g++ -g $^ -static -lprotobuf -lpthread -o ../$@

t_socket_sink: MistMessage.pb.o GateTalk.pb.o SpnMessage.pb.o t_socket_sink.o
	g++ -g $^ -static -lprotobuf -lpthread -lcrypto -ldl -o ../$@

t_socket_source: MistMessage.pb.o GateTalk.pb.o t_socket_source.o
	g++ -g $^ -static -lprotobuf -lpthread -lcrypto -ldl -o ../$@

t_multi_sink: MistMessage.pb.o SpnMessage.pb.o t_multi_sink.o
	g++ -g $^ -static -lprotobuf -lpthread -lcrypto -ldl -o ../$@

t_multi_source: MistMessage.pb.o t_multi_source.o
	g++ -g $^ -static -lprotobuf -lpthread -lcrypto -ldl -o ../$@

t_socket_sink_new: MistMessage.pb.o GateTalk.pb.o SpnMessage.pb.o t_socket_sink_new.o
	g++ -g3 $^ -static -lprotobuf -lpthread -lcrypto -ldl -o ../$@

t_socket_source_new: MistMessage.pb.o GateTalk.pb.o t_socket_source_new.o
	g++ -g3 $^ -static -lprotobuf -lpthread -lcrypto -ldl -o ../$@        
    
t_multi_sink_new: MistMessage.pb.o SpnMessage.pb.o t_multi_sink_new.o
	g++ -g3 $^ -static -lprotobuf -lpthread -lcrypto -ldl -o ../$@

t_multi_source_new: MistMessage.pb.o t_multi_source_new.o
	g++ -g3 $^ -static -lprotobuf -lpthread -lcrypto -ldl -o ../$@    
    
%.pb.cc: ../../../src/proto/%.proto
	protoc -I../../../src/proto --cpp_out=. $^

%.pb.o: %.pb.cc

%.o: %.cc
	g++ -g -c $^ -o $@

clean:
	rm -f *.pb.*
	rm -f *.o


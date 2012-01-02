#ifndef _MIST_CORE
#define _MIST_CORE

#define MAX_MSG_SIZE 512000
#define MISTD_PORT 9498

#include <mist_proto/MistMessage.pb.h>
#include <mist_proto/GateTalk.pb.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <iostream>
#include <arpa/inet.h>

int read_all(int blocking_fd, char* buf, size_t size) {
    size_t size_read = 0;
    while (size_read < size) {
        int n = read(blocking_fd, buf + size_read, size - size_read);
        if (n <= 0)
            return n;
        size_read += n;
    }

    return size_read;
}

int connectTo(int port){
    int sock=socket(AF_INET,SOCK_STREAM,0);
    struct sockaddr_in sa;
    sa.sin_family=AF_INET;
    inet_aton("127.0.0.1",(struct in_addr *)&sa.sin_addr.s_addr);
    sa.sin_port=htons(port);

    if(connect(sock,(struct sockaddr*)&sa,sizeof(sa))<0){
        close(sock);
        return -1;
    }
    else
        return sock;
}

int sendRequest(const com::trendmicro::mist::proto::Command& req, com::trendmicro::mist::proto::Command& res){
    int sock;
    if((sock=connectTo(MISTD_PORT))<0)
        return -1;

    uint32_t byteSize=htonl(req.ByteSize());
    write(sock,&byteSize,4);
    req.SerializeToFileDescriptor(sock);

    read(sock,&byteSize,4);
    if(res.ParseFromFileDescriptor(sock)){
        close(sock);
        return 0;
    }
    else{
        close(sock);
        return -1;
    }
}

typedef struct message_payload{
	const char* buf;
	const size_t len;
	message_payload(const char* buf_, const size_t len_): buf(buf_), len(len_) {}
}message_payload;

template
<
	class source_block_policy,
	class sink_block_policy,
	template <class> class source_policy,
	template <class> class sink_policy
>
class Processor : 	public source_policy<source_block_policy>, 
					public sink_policy<sink_block_policy>
{
	using source_policy<source_block_policy>::Read;
	using sink_policy<sink_block_policy>::Write;

	public:
		bool process_one(){
			message_payload payload = Read();
			if(payload.len <= 0){
				return false;
			}
			Write(payload.buf, payload.len);
			return true;
		}

		void run(){
			for(;;){
				message_payload payload = Read();
				if(payload.len <= 0){
					break;
				}
				Write(payload.buf, payload.len);
			}
		}
};

class Block_Base{
	public:
		Block_Base(){
			_buf = new char[MAX_MSG_SIZE];
		}

		~Block_Base(){
			delete[] _buf;
		}

	protected:
		char* _buf;
};

template < class block_policy >
class Read_Stdin_Policy : public block_policy
{
	protected:
		message_payload Read(){
			return block_policy::Read(0);
		}
};

class Socket_Policy_Base{
	public:
		void set_sock_fd(int sock){
			_sock = sock;
		}

	protected:
		int _sock;
};

template < class block_policy >
class Read_Socket_Policy : public block_policy, public Socket_Policy_Base
{
	protected:
		message_payload Read(){
			return block_policy::Read(_sock);
		}
};

template < class block_policy >
class Write_Socket_Policy : public block_policy, public Socket_Policy_Base
{
	protected:
		void Write(const char* buf, const size_t count){
			return block_policy::Write(_sock, buf, count);
		}
};

template < class block_policy >
class Write_Stdout_Policy : public block_policy
{
	protected:
		void Write(const char* buf, const size_t count){
			block_policy::Write(1, buf, count);
		}
};

class Block_Policy_Line : public Block_Base
{
	protected:
		message_payload Read(const int fd){
			char* ptr = _buf;
			size_t size = 0;
			while(read_all(fd, ptr , 1) == 1 && *ptr != '\n'){
				if((size = ptr - _buf + 1) == MAX_MSG_SIZE){
					throw "Maximum message size exceeded";
				}
				ptr++;
			}
			*ptr = '\0';

			return message_payload(_buf, size);
		}

		void Write(const int fd, const char* buf, const size_t count){
			write(fd, buf, count);
			write(fd, "\n", 1);
		}
};

class Block_Policy_Length : public Block_Base
{
	protected:
		message_payload Read(const int fd){
			uint32_t len = 0;
			if(read_all(fd, _buf, 4) == 4){
				len = ntohl(*((uint32_t*)_buf));
				read_all(fd, _buf, len);
			}
			return message_payload(_buf, len);
		}

		void Write(const int fd, const char* buf, const size_t count){
			uint32_t len = htonl(count);
			write(fd, &len, 4);
			write(fd, buf, count);
		}
};

class Block_Policy_Skip : public Block_Policy_Length
{
	protected:
		message_payload Read(const int fd){
			Block_Policy_Length::Read(fd);
			return message_payload(_buf, 0);
		}
};

class Block_Policy_MessageBlock
{
	public:
		void set_id(std::string id){
			_id = id;
		}
		
	protected:
		message_payload Read(const int fd){
			using namespace google::protobuf::io;
			static FileInputStream fis(fd);
			static CodedInputStream cis(&fis);

			const char* msgbuf;
			size_t len = 0;
			char buf[4];
			if(cis.ReadRaw(buf, 4)){
				len = ntohl(*((uint32_t*)buf));
				int limit = cis.PushLimit(len);
				_msg.ParseFromCodedStream(&cis);
				msgbuf = _msg.message().data();
				len = _msg.message().size();
				cis.PopLimit(limit);
			}
			return message_payload(msgbuf, len);
		}

		void Write(const int fd, const char* buf, const size_t count){
			_msg.set_id(_id);
			_msg.set_message(buf, count);
			uint32_t len = htonl(_msg.ByteSize());
			write(fd, &len, 4);
			_msg.SerializeToFileDescriptor(fd);
		}

	private:
		std::string _id;
		com::trendmicro::mist::proto::MessageBlock _msg;
};

#endif


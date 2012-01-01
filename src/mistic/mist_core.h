#ifndef _MIST_CORE
#define _MIST_CORE

#define MAX_MSG_SIZE 512000

#include <mist_proto/MistMessage.pb.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <iostream>
#include <arpa/inet.h>

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
		void run(){
			while(true){
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
			while(read(fd, ptr , 1) == 1 && *ptr != '\n'){
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
			if(read(fd, _buf, 4) == 4){
				len = ntohl(*((uint32_t*)_buf));
				read(fd, _buf, len);
			}
			return message_payload(_buf, len);
		}

		void Write(const int fd, const char* buf, const size_t count){
			uint32_t len = htonl(count);
			write(fd, &len, 4);
			write(fd, buf, count);
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
			uint32_t len = 0;
			const char* msgbuf;
			int size = 0;
			char _buf[4];
			if(read(fd, _buf, 4) == 4){
				len = ntohl(*((uint32_t*)_buf));

				using namespace google::protobuf::io;
				static FileInputStream fis(fd);
				static CodedInputStream cis(&fis);
				int limit = cis.PushLimit(len);
				
				_msg.ParseFromCodedStream(&cis);
				msgbuf = _msg.message().data();
				size = _msg.message().size();
				cis.PopLimit(limit);
			}
			return message_payload(msgbuf, size);
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


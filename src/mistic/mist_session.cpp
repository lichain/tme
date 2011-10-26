/*
 * mist_session.cpp
 *
 *  Created on: Oct 24, 2011
 *      Author: Scott Wang <scott_wang@trend.com.tw>
 */

#include<iostream>
#include<fstream>
#include<arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <netdb.h>
#include <netinet/tcp.h>
#include<signal.h>
#include<unistd.h>
#include<stdlib.h>
#include<boost/program_options.hpp>
#include "mist_protos/GateTalk.pb.h"

#define MISTD_PORT 9498

using namespace com::trendmicro::mist::proto;
using namespace std;

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

int sendRequest(const Command& req, Command& res){
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

int read_all(int blocking_fd, char* buf, size_t size)
{
    size_t size_read = 0;
    while(size_read < size)
    {
        int n = read(blocking_fd, buf + size_read, size - size_read);
        if(n <= 0)
            return n;
        size_read += n;
    }

    return size_read;
}

int create_session() {
	int session_id = -1;

	Command cmd;
	Session* sessPtr = cmd.add_session();
	sessPtr->mutable_connection()->set_host_name("");
	sessPtr->mutable_connection()->set_host_port("");
	sessPtr->mutable_connection()->set_username("");
	sessPtr->mutable_connection()->set_password("");
	sessPtr->mutable_connection()->set_broker_type("");

	Command res;
	if (sendRequest(cmd, res) == 0)
		if (res.response(0).success())
			session_id = atoi(res.response(0).context().c_str());

	return session_id;
}

void destroy_session(const string& session_id) {
	Command cmd;
	Request* reqPtr = cmd.add_request();
	reqPtr->set_type(Request::SESSION_DESTROY);
	reqPtr->set_argument(session_id);

	Command res;
	if (sendRequest(cmd, res) == 0) {
		if (res.response(0).success()) {
			cerr<<res.response(0).context()<<endl;
		}
		else{
			cerr<<res.response(0).exception()<<endl;
		}
	}
}

int main(int argc, char* argv[]) {
	namespace program_opt = boost::program_options;

	program_opt::options_description opt_desc("Allowed options");
	opt_desc.add_options()("help", "Display help messages")("destroy,d", program_opt::value<string>(),"Destroy session");

	program_opt::variables_map var_map;
	program_opt::store(program_opt::parse_command_line(argc, argv, opt_desc),
			var_map);
	program_opt::notify(var_map);

	if( var_map.count("help")){
		cerr << opt_desc << endl;
				return 1;
	}
	else if(var_map.count("destroy")){
		destroy_session(var_map["destroy"].as<string>());
	}
	else{
		cout<<create_session()<<endl;
	}
	return 0;
}

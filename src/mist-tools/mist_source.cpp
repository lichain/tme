/*
 * mist_source.cpp
 *
 *  Created on: Oct 24, 2011
 *      Author: Scott Wang <scott_wang@trend.com.tw>
 */

#include "mist_core.h"

#include<iostream>
#include<fstream>
#include<netinet/tcp.h>
#include<signal.h>
#include<unistd.h>
#include<boost/program_options.hpp>

using namespace std;
using namespace com::trendmicro::mist::proto;

bool on_close = false;

int sock;

bool attach(const string& session_id, bool ack, long limit){
	long msg_cnt = 0;
	string filename = string("/var/run/tme/pid/") + session_id + string(".pid");

	ofstream ofs(filename.c_str());
	ofs << getpid() << endl;
	ofs.close();

	Command req_cmd;
	Command res;
	Request* req_ptr = req_cmd.add_request();
	req_ptr->set_type(Request::CLIENT_ATTACH);
	req_ptr->set_argument(session_id);
	req_ptr->set_role(Request::SOURCE);

	Response ackResponse;
	ackResponse.set_success(true);
	uint32_t ackSize=htonl(ackResponse.ByteSize());
	if (sendRequest(req_cmd, res)) {
		if (res.response(0).success()) {
			sock = connectTo(atoi(res.response(0).context().c_str()));
			int trueflag = 1;
			setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, &trueflag, sizeof(int));

			Processor<Block_Policy_Length, Block_Policy_Length, Read_Socket_Policy, Write_Stdout_Policy> processor;
			processor.set_sock_fd(sock);
			
			while(!on_close && processor.process_one()){
				if(ack){
					sigset_t waitset;
					int sig;
					int result;
					sigemptyset(&waitset);
					sigaddset(&waitset, SIGUSR1);
					sigprocmask(SIG_BLOCK, &waitset, NULL);
					result = sigwait(&waitset, &sig);
				}
				write(sock,&ackSize,4);
				ackResponse.SerializeToFileDescriptor(sock);

				if(++msg_cnt == limit){
					on_close = true;
				}
			}
			close(sock);
			return true;
		}
		else{
		    cerr<<res.response(0).exception()<<endl;
		}
	}
	return false;
}

bool detach(const string& session_id){
	Command req_cmd;
	Command res;
	Request* req_ptr = req_cmd.add_request();
	req_ptr->set_type(Request::CLIENT_DETACH);
	req_ptr->set_argument(session_id);
	if (sendRequest(req_cmd, res)){
	    if (res.response(0).success()){
		cerr<<res.response(0).context()<<endl;
		return true;
	    }
	    else{
		cerr<<res.response(0).exception()<<endl;
	    }
	}
	return false;
}

bool mount(const string& session_id, const string& exName) {
	Command cmd;
	Client* clientPtr = cmd.add_client();
	clientPtr->set_session_id(atoi(session_id.c_str()));
	clientPtr->mutable_channel()->set_name(exName);
	clientPtr->mutable_channel()->set_type(Channel::QUEUE);
	clientPtr->set_type(Client::CONSUMER);
	clientPtr->set_action(Client::MOUNT);

	Command res;
	if (sendRequest(cmd, res)){
		if (res.response(0).success()){
			cerr<<res.response(0).context()<<endl;
			return true;
		}
		else{
					cerr<<res.response(0).exception()<<endl;
				}
	}
	return false;
}

bool unmount(const string& session_id, const string& exName) {
	Command cmd;
	Client* clientPtr = cmd.add_client();
	clientPtr->set_session_id(atoi(session_id.c_str()));
	clientPtr->mutable_channel()->set_name(exName);
	clientPtr->mutable_channel()->set_type(Channel::QUEUE);
	clientPtr->set_type(Client::CONSUMER);
	clientPtr->set_action(Client::UNMOUNT);

	Command res;
	if (sendRequest(cmd, res)){
		if (res.response(0).success()){
			cerr<<res.response(0).context()<<endl;
			return true;
		}
		else{
					cerr<<res.response(0).exception()<<endl;
				}
	}
	return false;
}

string session_id;

void cleanup() {
	unlink((string("/var/run/tme/pid/") + session_id + string(".pid")).c_str());
}

void handler(int signo){
	close(sock);
	on_close = true;
}

int main(int argc, char* argv[]) {
	atexit(cleanup);
	signal(SIGINT, handler);

	namespace program_opt = boost::program_options;

	program_opt::options_description opt_desc("Allowed options");
	opt_desc.add_options()("help", "Display help messages")("attach,a", "Attach session")("session-id,s", "Session ID")
			("ack,A", "Manual ACK messages")("mount,m", program_opt::value<string>(),"Mount exchange")
			("unmount,u", program_opt::value<string>(), "Unmount exchange")("detach,d", "Detach session")
			("limit,l", program_opt::value<long>(), "Retrieve count limit");

	program_opt::positional_options_description pos_opt_desc;
	pos_opt_desc.add("session-id", -1);

	program_opt::variables_map var_map;
	program_opt::store(program_opt::command_line_parser(argc, argv).options(opt_desc).positional(pos_opt_desc).run(), var_map);
	program_opt::notify(var_map);

	if(var_map.count("help")){
		cerr << opt_desc << endl;
		return 0;
	}

	if(!var_map.count("session-id")){
		cerr << opt_desc << endl;
		return MIST_ARGUMENT_ERROR;
	}
	session_id = var_map["session-id"].as<string>();

	if (var_map.count("attach")) {
		if(!attach(var_map["session-id"].as<string>(), var_map.count("ack")>0, var_map.count("limit")>0 ? var_map["limit"].as<long>() : -1)){
			return MIST_SOURCE_ATTACH_ERROR;
		}
	}
	else if (var_map.count("mount")) {
		if(!mount(var_map["session-id"].as<string>(), var_map["mount"].as<string>())){
			return MIST_SOURCE_MOUNT_ERROR;
		}
	}
	else if (var_map.count("unmount")) {
		if(!unmount(var_map["session-id"].as<string>(), var_map["unmount"].as<string>())){
			return MIST_SOURCE_UNMOUNT_ERROR;
		}
	}
	else if (var_map.count("detach")) {
		if(!detach(var_map["session-id"].as<string>())){
			return MIST_SOURCE_DETACH_ERROR;
		}
	}
	return 0;
}

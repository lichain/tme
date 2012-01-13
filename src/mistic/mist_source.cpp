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

void attach(const string& session_id, bool ack){
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
	if (sendRequest(req_cmd, res) == 0) {
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
			}
			close(sock);
		}
	}
}

void detach(const string& session_id){
	Command req_cmd;
	Command res;
	Request* req_ptr = req_cmd.add_request();
	req_ptr->set_type(Request::CLIENT_DETACH);
	req_ptr->set_argument(session_id);
	if (sendRequest(req_cmd, res) == 0){
	    if (res.response(0).success()){
		cerr<<res.response(0).context()<<endl;
	    }
	    else{
		cerr<<res.response(0).exception()<<endl;
	    }
	}
}

void mount(const string& session_id, const string& exName) {
	Command cmd;
	Client* clientPtr = cmd.add_client();
	clientPtr->set_session_id(atoi(session_id.c_str()));
	clientPtr->mutable_channel()->set_name(exName);
	clientPtr->mutable_channel()->set_type(Channel::QUEUE);
	clientPtr->set_type(Client::CONSUMER);
	clientPtr->set_action(Client::MOUNT);

	Command res;
	if (sendRequest(cmd, res) == 0){
		if (res.response(0).success()){
			cerr<<res.response(0).context()<<endl;
		}
		else{
					cerr<<res.response(0).exception()<<endl;
				}
	}
}

void unmount(const string& session_id, const string& exName) {
	Command cmd;
	Client* clientPtr = cmd.add_client();
	clientPtr->set_session_id(atoi(session_id.c_str()));
	clientPtr->mutable_channel()->set_name(exName);
	clientPtr->mutable_channel()->set_type(Channel::QUEUE);
	clientPtr->set_type(Client::CONSUMER);
	clientPtr->set_action(Client::UNMOUNT);

	Command res;
	if (sendRequest(cmd, res) == 0){
		if (res.response(0).success()){
			cerr<<res.response(0).context()<<endl;
		}
		else{
					cerr<<res.response(0).exception()<<endl;
				}
	}
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
			("unmount,u", program_opt::value<string>(), "Unmount exchange")("detach,d", "Detach session");

	program_opt::positional_options_description pos_opt_desc;
	pos_opt_desc.add("session-id", -1);

	program_opt::variables_map var_map;
	program_opt::store(program_opt::command_line_parser(argc, argv).options(opt_desc).positional(pos_opt_desc).run(), var_map);
	program_opt::notify(var_map);

	if(!var_map.count("session-id")){
		cerr << opt_desc << endl;
				return 1;
	}
	session_id = var_map["session-id"].as<string>();

	if (var_map.count("attach")) {
		attach(var_map["session-id"].as<string>(), var_map.count("ack")>0);
	}
	else if (var_map.count("mount")) {
		mount(var_map["session-id"].as<string>(), var_map["mount"].as<string>());
	}
	else if (var_map.count("unmount")) {
		unmount(var_map["session-id"].as<string>(), var_map["unmount"].as<string>());
	}
	else if (var_map.count("detach")) {
		detach(var_map["session-id"].as<string>());
	}
	else {
		cerr << opt_desc << endl;
		return 1;
	}
	return 0;
}

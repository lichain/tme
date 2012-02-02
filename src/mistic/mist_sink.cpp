#include "mist_core.h"

#include<iostream>
#include<fstream>
#include<stdlib.h>

#include<arpa/inet.h>
#include<netinet/in.h>
#include<sys/socket.h>
#include<netdb.h>
#include<netinet/tcp.h>
#include<sys/file.h>
#include<signal.h>
#include<unistd.h>
#include<boost/program_options.hpp>

using namespace std;
using namespace com::trendmicro::mist::proto;


string ack_session_id;

void manual_ack_loop(istream& is, int sock){
	const uint32_t ACK_HEADER = ntohl(*((uint32_t*) &"ACK\n"));
	int pid = -1;
	for (;;) {
		uint32_t besize;
		if (!cin.read((char*) &besize, 4)) {
			break;
		}

		if (pid < 0) {
			string filename = string("/var/run/tme/pid/") + ack_session_id
					+ string(".pid");
			ifstream ifs(filename.c_str());
			ifs>>pid;
			ifs.close();
			cerr<<"pid="<<pid<<endl;
			cerr.flush();
		}

		if(ntohl(besize) == ACK_HEADER){
			cout.flush();
			kill(pid, SIGUSR1);
			continue;
		}


		char buf[1024];
		ssize_t total = ntohl(besize);
		ssize_t nwrite = 0;
		write(sock, (char*) &besize, 4);
		while (nwrite != total) {
			ssize_t size_to_read = (total - nwrite) < 1024 ? total - nwrite : 1024;
			cin.read(buf, size_to_read);
			write(sock, buf, size_to_read);
			nwrite += size_to_read;
			//cerr<<"n: "<<n<<" nw:"<<nw<<" nread:"<<nread<<endl;
		}

		if (read(sock, &besize, 4) <= 0){
			break;
		}
		total = ntohl(besize);
		ssize_t nread = 0;
		while (nread != total) {
			ssize_t n = read(sock, buf,
					(total - nread) < 1024 ? total - nread : 1024);
			nread += n;
			//cerr<<"n: "<<n<<" nw:"<<nw<<" nread:"<<nread<<endl;
		}



	}
}

void attach(const string& session_id, bool ack, bool counting) {
	Command req_cmd;
	Command res;
	Request* req_ptr = req_cmd.add_request();
	req_ptr->set_type(Request::CLIENT_ATTACH);
	req_ptr->set_argument(session_id);
	req_ptr->set_role(Request::SINK);

	unsigned long count = 0;
	Response ackResponse;
	ackResponse.set_success(true);
	uint32_t ackSize = htonl(ackResponse.ByteSize());
	if (sendRequest(req_cmd, res) == 0) {
		if (res.response(0).success()) {

			int sock = connectTo(atoi(res.response(0).context().c_str()));
			int trueflag = 1;
			setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, &trueflag, sizeof(int));

			if(ack){
				manual_ack_loop(cin, sock);
			}
			else{
				Processor<Block_Policy_Length, Block_Policy_Length, Read_Stdin_Policy, Write_Socket_Policy> processor;
				Processor<Block_Policy_Skip, Block_Policy_Length, Read_Socket_Policy, Write_Stdout_Policy> response_processor;
				processor.set_sock_fd(sock);
				response_processor.set_sock_fd(sock);
				while(processor.process_one()){
					response_processor.process_one();
					if(counting){
						cerr<<++count<<" messages delivered"<<endl;
					}
				}
			}
			close(sock);
		}
	}

}

void detach(const string& session_id) {
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

int main(int argc, char* argv[]) {
	namespace program_opt = boost::program_options;

	program_opt::options_description opt_desc("Allowed options");


	opt_desc.add_options()("help", "Display help messages")("attach,a",
			"Attach session")("session-id,s", "Session ID")("ack,A", program_opt::value<string>(&ack_session_id),
			"Manual ACK messages")("detach,d", "Detach session")
			("count,c", "Output delivered message count");

	program_opt::positional_options_description pos_opt_desc;
	pos_opt_desc.add("session-id", -1);

	program_opt::variables_map var_map;
	program_opt::store(
			program_opt::command_line_parser(argc, argv).options(opt_desc).positional(
					pos_opt_desc).run(), var_map);
	program_opt::notify(var_map);

	if (!var_map.count("session-id")) {
		cerr << opt_desc << endl;
		return 1;
	}
	string session_id = var_map["session-id"].as<string> ();

	if (var_map.count("attach")) {
		attach(session_id, var_map.count("ack") > 0, var_map.count("count") > 0);
	} 
	else if (var_map.count("detach")) {
		detach(session_id);
	} 
	else {
		cerr << opt_desc << endl;
		return 1;
	}
	return 0;
}

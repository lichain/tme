/*
 * mist_source.cpp
 *
 *  Created on: Oct 24, 2011
 *      Author: Scott Wang <scott_wang@trend.com.tw>
 */

#include <mist_proto/MistMessage.pb.h>
#include <mist_proto/SpnMessage.pb.h>
#include <mist_proto/GateTalk.pb.h>

#include<iostream>
#include<fstream>
#include<stdlib.h>

#include<arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <netdb.h>
#include <netinet/tcp.h>
#include <sys/file.h>
#include<signal.h>
#include<unistd.h>
#include<boost/program_options.hpp>

#define MISTD_PORT 9498

using namespace com::trendmicro::mist::proto;
using namespace com::trendmicro::spn::proto;
using namespace std;

bool on_close = false;

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
	//char* buf=NULL;
	if (sendRequest(req_cmd, res) == 0) {
		if (res.response(0).success()) {

			int sock = connectTo(atoi(res.response(0).context().c_str()));
			cerr << "port: " << atoi(res.response(0).context().c_str())
					<< " ; sock = " << sock << endl;
			int trueflag = 1;
			setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, &trueflag, sizeof(int));

			for (;;) {
				MessageBlock msg;
				uint32_t besize;
				if (on_close || read(sock, &besize, 4) <= 0){
					break;
				}

/*				buf = (char*) malloc(ntohl(besize) + 200);
				read_all(sock, buf, ntohl(besize));
				msg.ParseFromArray(buf, ntohl(besize));

				cout.write((char*)&besize, 4);
				msg.SerializeToOstream(&cout);*/

				//cout.write((char*)&besize, 4);

				char buf[1024];
				ssize_t total = ntohl(besize);
				ssize_t nread = 0;
				write(fileno(stdout), (char*)&besize, 4);
				while (nread != total) {
					ssize_t n = read(sock, buf, (total - nread) < 1024 ? total - nread : 1024);
					int nw = write(fileno(stdout), buf, n);
					nread += n;
					//cerr<<"n: "<<n<<" nw:"<<nw<<" nread:"<<nread<<endl;
				}

				//while()
				//cout.rdbuf();
				cout.flush();

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

				//free(buf);
			}
			close(sock);

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

string session_id;

void cleanup() {
	unlink((string("/var/run/tme/pid/") + session_id + string(".pid")).c_str());
}




void handler(int signo){
	on_close = true;
	/*cerr<<"handler"<<endl;
	Command req_cmd;
	Command res;
	Request* req_ptr = req_cmd.add_request();
	req_ptr->set_type(Request::CLIENT_DETACH);
	req_ptr->set_argument(session_id);
	req_ptr->set_role(Request::SOURCE);
	sendRequest(req_cmd, res);*/
}



int main(int argc, char* argv[]) {
	atexit(cleanup);
	signal(SIGINT, handler);

	namespace program_opt = boost::program_options;

	program_opt::options_description opt_desc("Allowed options");
	opt_desc.add_options()("help", "Display help messages")("attach,a", "Attach session")("session-id,s", "Session ID")
			("ack,A", "Manual ACK messages")("mount,m", program_opt::value<string>(),"Mount exchange");

	program_opt::positional_options_description pos_opt_desc;
	pos_opt_desc.add("session-id", -1);

	program_opt::variables_map var_map;
	//program_opt::store(program_opt::parse_command_line(argc, argv, opt_desc),
		//	var_map);
	program_opt::store(program_opt::command_line_parser(argc, argv).options(opt_desc).positional(pos_opt_desc).run(), var_map);
	program_opt::notify(var_map);

	if(!var_map.count("session-id")){
		cerr << opt_desc << endl;
				return 1;
	}
	session_id = var_map["session-id"].as<string>();

	if (var_map.count("attach")) {
		attach(var_map["session-id"].as<string>(), var_map.count("ack")>0);
		//process_line(var_map.count("raw") > 0, var_map.count("ack") > 0);
	}
	else if (var_map.count("mount")) {
		mount(var_map["session-id"].as<string>(), var_map["mount"].as<string>());
	}
	else {
		cerr << opt_desc << endl;
		return 1;
	}
	return 0;
}

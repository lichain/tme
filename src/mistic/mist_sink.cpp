#include <mist_proto/MistMessage.pb.h>
#include <mist_proto/GateTalk.pb.h>

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

#define MISTD_PORT 9498

using namespace com::trendmicro::mist::proto;
using namespace std;

const uint32_t ACK_HEADER = ntohl(*((uint32_t*) &"ACK\n"));

string ack_session_id;

int connectTo(int port) {
	int sock = socket(AF_INET, SOCK_STREAM, 0);
	struct sockaddr_in sa;
	sa.sin_family = AF_INET;
	inet_aton("127.0.0.1", (struct in_addr *) &sa.sin_addr.s_addr);
	sa.sin_port = htons(port);

	if (connect(sock, (struct sockaddr*) &sa, sizeof(sa)) < 0) {
		close(sock);
		return -1;
	} else
		return sock;
}

int sendRequest(const Command& req, Command& res) {
	int sock;
	if ((sock = connectTo(MISTD_PORT)) < 0)
		return -1;

	uint32_t byteSize = htonl(req.ByteSize());
	write(sock, &byteSize, 4);
	req.SerializeToFileDescriptor(sock);

	read(sock, &byteSize, 4);
	if (res.ParseFromFileDescriptor(sock)) {
		close(sock);
		return 0;
	} else {
		close(sock);
		return -1;
	}
}

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

void manual_ack_loop(istream& is, int sock){
	int pid = -1;
	int cnt = 0;
	time_t t = time(NULL);
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
			cout<<"pid="<<pid<<endl;

		}

		if(cnt % 10000 == 0){
					cout<<"forwarded count: "<<cnt++<<endl;
					cout<<"sec: "<<(float)10000/(time(NULL) - t)<<endl;
					cout.flush();
					t = time(NULL);
					//cout<<"avg: "
				}

		cnt++;
		cout.flush();
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

void auto_ack_loop(istream& is, int sock){
	int cnt = 0;
		time_t t = time(NULL);
		for (;;) {
			uint32_t besize;
			if (!cin.read((char*) &besize, 4)) {
				break;
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

			cnt++;
			if(cnt % 10000 == 0){
				cout<<"forwarded count: "<<cnt++<<endl;
				cout<<"sec: "<<(float)10000/(time(NULL) - t)<<endl;
				cout.flush();
				t = time(NULL);
				//cout<<"avg: "
			}
		}
}

void attach(const string& session_id, bool ack) {
	Command req_cmd;
	Command res;
	Request* req_ptr = req_cmd.add_request();
	req_ptr->set_type(Request::CLIENT_ATTACH);
	req_ptr->set_argument(session_id);
	req_ptr->set_role(Request::SINK);

	Response ackResponse;
	ackResponse.set_success(true);
	uint32_t ackSize = htonl(ackResponse.ByteSize());
	//char* buf=NULL;
	if (sendRequest(req_cmd, res) == 0) {
		if (res.response(0).success()) {

			int sock = connectTo(atoi(res.response(0).context().c_str()));
			cerr << "port: " << atoi(res.response(0).context().c_str())
					<< " ; sock = " << sock << endl;
			int trueflag = 1;
			setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, &trueflag, sizeof(int));



			if(ack){
				manual_ack_loop(cin, sock);
			}
			else{
				auto_ack_loop(cin, sock);
			}
			close(sock);

		}
	}

}

int main(int argc, char* argv[]) {
	namespace program_opt = boost::program_options;

	program_opt::options_description opt_desc("Allowed options");


	opt_desc.add_options()("help", "Display help messages")("attach,a",
			"Attach session")("session-id,s", "Session ID")("ack,A", program_opt::value<string>(&ack_session_id),
			"Manual ACK messages");

	program_opt::positional_options_description pos_opt_desc;
	pos_opt_desc.add("session-id", -1);

	program_opt::variables_map var_map;
	//program_opt::store(program_opt::parse_command_line(argc, argv, opt_desc),
	//	var_map);
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
		attach(session_id, var_map.count("ack") > 0);
		//process_line(var_map.count("raw") > 0, var_map.count("ack") > 0);
	} else {
		cerr << opt_desc << endl;
		return 1;
	}
	return 0;
}

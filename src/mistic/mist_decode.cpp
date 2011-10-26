/*
 * mist_decode.cpp
 *
 *  Created on: Oct 24, 2011
 *      Author: Scott Wang <scott_wang@trend.com.tw>
 */

#include<iostream>
#include<arpa/inet.h>
#include<boost/program_options.hpp>
#include"mist_protos/MistMessage.pb.h"
#include"mist_protos/SpnMessage.pb.h"

using namespace com::trendmicro::mist::proto;
using namespace com::trendmicro::spn::proto;
using namespace std;

void process_line(bool raw) {
	uint32_t payload_length;

	while (cin.read((char*) &payload_length, 4)) {
		payload_length = ntohl(payload_length);
		char* buf = new char[payload_length];
		cin.read(buf, payload_length);

		MessageBlock msg;
		msg.ParseFromArray(buf, payload_length);
		if (raw) {
			cout << msg.message() << endl;
		} else {
			Container container;
			container.ParseFromString(msg.message());
			cout
					<< container.container_base().message_list().messages(0).derived()
					<< endl;
		}
		cout.flush();

		delete[] buf;
	}
}

int main(int argc, char* argv[]) {
	namespace program_opt = boost::program_options;

	program_opt::options_description opt_desc("Allowed options");
	opt_desc.add_options()("help", "Display help messages")("line,l",
			"Decode message stream into line text file")("raw,r",
			"Do not unpack the message from SPN Message V2");

	program_opt::variables_map var_map;
	program_opt::store(program_opt::parse_command_line(argc, argv, opt_desc),
			var_map);
	program_opt::notify(var_map);

	if (var_map.count("line")) {
		process_line(var_map.count("raw") > 0);
	} else {
		cerr << opt_desc << endl;
		return 1;
	}
	return 0;
}

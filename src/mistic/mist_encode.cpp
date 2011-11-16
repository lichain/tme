/*
 * mist_encode.cpp
 *
 *  Created on: Oct 24, 2011
 *      Author: Scott Wang <scott_wang@trend.com.tw>
 */

#include <mist_proto/MistMessage.pb.h>

#include<string>
#include<iostream>

#include<arpa/inet.h>
#include<boost/program_options.hpp>

using namespace com::trendmicro::mist::proto;
using namespace std;

void process_line(const string& message_id) {
	string line;
	while (getline(cin, line)) {
		MessageBlock msg;
		msg.set_id(message_id);
		msg.set_message(line);
		uint32_t payload_length = htonl(msg.ByteSize());
		cout.write((char*) &payload_length, 4);
		msg.SerializeToOstream(&cout);
	}
}

int main(int argc, char* argv[]) {
	namespace program_opt = boost::program_options;

	program_opt::options_description opt_desc("Allowed options");
	opt_desc.add_options()("help", "Display help messages")("line,l",
			"Encode each text line as a message")(
			"wrap,w",
			program_opt::value<string>(),
			"wrap as message block of MESSAGEID\nMESSAGEID={queue|topic}:EXCHANGENAME\nif exchange type prefix is not given, default to queue");

	program_opt::variables_map var_map;
	program_opt::store(program_opt::parse_command_line(argc, argv, opt_desc),
			var_map);
	program_opt::notify(var_map);

	string message_id;
	if (!var_map.count("wrap")) {
		cout << "No MESSAGEID is set! Please use --wrap to set MESSAGEID"
				<< endl;
		cout << opt_desc << endl;
		return 1;
	}
	message_id = var_map["wrap"].as<string> ();

	if (var_map.count("line")) {
		process_line(message_id);
	} else {
		cout << opt_desc << endl;
		return 1;
	}
	return 0;
}

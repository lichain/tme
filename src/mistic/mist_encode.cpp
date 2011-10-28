/*
 * mist_encode.cpp
 *
 *  Created on: Oct 24, 2011
 *      Author: Scott Wang <scott_wang@trend.com.tw>
 */

#include <proto/MistMessage.pb.h>
#include <proto/SpnMessage.pb.h>

#include<string>
#include<iostream>

#include<arpa/inet.h>
#include<boost/program_options.hpp>

using namespace com::trendmicro::mist::proto;
using namespace com::trendmicro::spn::proto;
using namespace std;

void process_line(bool raw, const string& message_id) {
	string line;
	while (getline(cin, line)) {
		MessageBlock msg;
		msg.set_id(message_id);
		if (raw) {
			msg.set_message(line);
		} else {
			Container container;
			container.mutable_container_base()->mutable_message_list()->add_messages()->set_derived(
					line);
			container.mutable_container_base()->mutable_message_list()->mutable_messages(
					0)->mutable_msg_base()->set_subject("");
			msg.set_message(container.SerializeAsString());
		}
		uint32_t payload_length = htonl(msg.ByteSize());
		cout.write((char*) &payload_length, 4);
		msg.SerializeToOstream(&cout);
	}
}

int main(int argc, char* argv[]) {
	namespace program_opt = boost::program_options;

	program_opt::options_description opt_desc("Allowed options");
	opt_desc.add_options()("help", "Display help messages")("line,l",
			"Encode each text line as a message")("raw,r",
			"Do not pack the message into SPN Message V2")(
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
		process_line(var_map.count("raw") > 0, message_id);
	} else {
		cout << opt_desc << endl;
		return 1;
	}
	return 0;
}

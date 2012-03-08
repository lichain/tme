/*
 * mist_encode.cpp
 *
 *  Created on: Oct 24, 2011
 *      Author: Scott Wang <scott_wang@trend.com.tw>
 */

#include "mist_core.h"

#include<string>
#include<iostream>

#include<arpa/inet.h>
#include<boost/program_options.hpp>

using namespace std;

int main(int argc, char* argv[]) {
	namespace program_opt = boost::program_options;

	program_opt::options_description opt_desc("Allowed options");
	opt_desc.add_options()("help", "Display help messages")("line,l",
			"Encode each text line as a message")("stream,s",
			"Process message in [length][payload] format, length is 4 byte big endian integer")(
			"wrap,w",
			program_opt::value<string>(),
			"wrap as message block of MESSAGEID\nMESSAGEID={queue|topic}:EXCHANGENAME\nif exchange type prefix is not given, default to queue")
			("ttl,t", program_opt::value<int>(), "set message TTL, in seconds");

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
		Processor<Block_Policy_Line, Block_Policy_MessageBlock, Read_Stdin_Policy, Write_Stdout_Policy> processor;
		processor.set_id(message_id);
		if(var_map.count("ttl")){
		    processor.set_ttl(var_map["ttl"].as<int>());
		}
		processor.run();
	}
	else if (var_map.count("stream")) {
		Processor<Block_Policy_Length, Block_Policy_MessageBlock, Read_Stdin_Policy, Write_Stdout_Policy> processor;
		processor.set_id(message_id);
		if(var_map.count("ttl")){
		    processor.set_ttl(var_map["ttl"].as<int>());
		}
		processor.run();
	} else {
		cout << opt_desc << endl;
		return 1;
	}
	return 0;
}

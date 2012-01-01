/*
 * mist_decode.cpp
 *
 *  Created on: Oct 24, 2011
 *      Author: Scott Wang <scott_wang@trend.com.tw>
 */

#include "mist_core.h"

#include<iostream>

#include<arpa/inet.h>
#include<boost/program_options.hpp>

using namespace std;

int main(int argc, char* argv[]) {
	namespace program_opt = boost::program_options;

	program_opt::options_description opt_desc("Allowed options");
	opt_desc.add_options()("help", "Display help messages")("line,l",
			"Decode message stream into line text file")("stream,s",
			"Decode message stream into [length][payload] format, length is 4 byte big endian integer");

	program_opt::variables_map var_map;
	program_opt::store(program_opt::parse_command_line(argc, argv, opt_desc),
			var_map);
	program_opt::notify(var_map);

	if (var_map.count("line")) {
		Processor<Block_Policy_MessageBlock, Block_Policy_Line, Read_Stdin_Policy, Write_Stdout_Policy> processor;
	    processor.run();
	} else if (var_map.count("stream")){
		Processor<Block_Policy_MessageBlock, Block_Policy_Length, Read_Stdin_Policy, Write_Stdout_Policy> processor;
	    processor.run();
	} else {
		cerr << opt_desc << endl;
		return 1;
	}
	return 0;
}

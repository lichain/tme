/*
 * mist_session.cpp
 *
 *  Created on: Oct 24, 2011
 *      Author: Scott Wang <scott_wang@trend.com.tw>
 */


#include "mist_core.h"

#include<iostream>
#include<fstream>
#include<boost/program_options.hpp>

using namespace com::trendmicro::mist::proto;
using namespace std;

int create_session() {
	int session_id = -1;

	Command cmd;
	Session* sessPtr = cmd.add_session();
	sessPtr->mutable_connection()->set_host_name("");
	sessPtr->mutable_connection()->set_host_port("");
	sessPtr->mutable_connection()->set_username("");
	sessPtr->mutable_connection()->set_password("");
	sessPtr->mutable_connection()->set_broker_type("");

	Command res;
	if (sendRequest(cmd, res))
		if (res.response(0).success())
			session_id = atoi(res.response(0).context().c_str());

	return session_id;
}

void destroy_session(const string& session_id) {
	Command cmd;
	Request* reqPtr = cmd.add_request();
	reqPtr->set_type(Request::SESSION_DESTROY);
	reqPtr->set_argument(session_id);

	Command res;
	if (sendRequest(cmd, res)) {
		if (res.response(0).success()) {
			cerr<<res.response(0).context()<<endl;
		}
		else{
			cerr<<res.response(0).exception()<<endl;
		}
	}
}

void list_session() {
	Command cmd;
	Request* reqPtr = cmd.add_request();
	reqPtr->set_type(Request::SESSION_LIST);

	Command res;
	if (sendRequest(cmd, res)) {
		if (res.response(0).success()) {
			cerr<<res.response(0).context()<<endl;
		}
		else{
			cerr<<res.response(0).exception()<<endl;
		}
	}
}

void show_status() {
	Command cmd;
	Request* reqPtr = cmd.add_request();
	reqPtr->set_type(Request::DAEMON_STATUS);

	Command res;
	if (sendRequest(cmd, res)) {
		if (res.response(0).success()) {
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
	opt_desc.add_options()("help", "Display help messages")("destroy,d", program_opt::value<string>(),"Destroy session")
	("list,l", "List all sessions")("status,s", "Show MIST daemon status");

	program_opt::variables_map var_map;
	program_opt::store(program_opt::parse_command_line(argc, argv, opt_desc),
			var_map);
	program_opt::notify(var_map);

	if( var_map.count("help")){
		cerr << opt_desc << endl;
				return 1;
	}
	else if(var_map.count("destroy")){
		destroy_session(var_map["destroy"].as<string>());
	}
	else if(var_map.count("list")){
		list_session();
	}
	else if(var_map.count("status")){
		show_status();
	}
	else{
		cout<<create_session()<<endl;
	}
	return 0;
}

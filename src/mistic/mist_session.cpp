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
	if (sendRequest(cmd, res)){
		if (res.response(0).success()){
			session_id = atoi(res.response(0).context().c_str());
		}
		else{
			cerr<<res.response(0).exception()<<endl;
		}
	}
	else{
	    cerr<<"Error communicate to MIST daemon"<<endl;
	}

	return session_id;
}

bool destroy_session(const string& session_id) {
	Command cmd;
	Request* reqPtr = cmd.add_request();
	reqPtr->set_type(Request::SESSION_DESTROY);
	reqPtr->set_argument(session_id);

	Command res;
	if (sendRequest(cmd, res)) {
		if (res.response(0).success()) {
			cerr<<res.response(0).context()<<endl;
			return true;
		}
		else{
			cerr<<res.response(0).exception()<<endl;
		}
	}
	else{
	    cerr<<"Error communicate to MIST daemon"<<endl;
	}
	return false;
}

bool list_session() {
	Command cmd;
	Request* reqPtr = cmd.add_request();
	reqPtr->set_type(Request::SESSION_LIST);

	Command res;
	if (sendRequest(cmd, res)) {
		if (res.response(0).success()) {
			cerr<<res.response(0).context()<<endl;
			return true;
		}
		else{
			cerr<<res.response(0).exception()<<endl;
		}
	}
	else{
	    cerr<<"Error communicate to MIST daemon"<<endl;
	}
	return false;
}

bool show_status() {
	Command cmd;
	Request* reqPtr = cmd.add_request();
	reqPtr->set_type(Request::DAEMON_STATUS);

	Command res;
	if (sendRequest(cmd, res)) {
		if (res.response(0).success()) {
			cerr<<res.response(0).context()<<endl;
			return true;
		}
		else{
			cerr<<res.response(0).exception()<<endl;
		}
	}
	else{
	    cerr<<"Error communicate to MIST daemon"<<endl;
	}
	return false;
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
	}
	else if(var_map.count("destroy")){
		if(!destroy_session(var_map["destroy"].as<string>())){
			return 2;
		}
	}
	else if(var_map.count("list")){
		if(!list_session()){
			return 3;
		}
	}
	else if(var_map.count("status")){
		if(!show_status()){
			return 4;
		}
	}
	else{
		int sess_id = create_session();
		if(sess_id != -1){
			cout<<create_session()<<endl;
		}
		else{
			return 1;
		}
	}
	return 0;
}

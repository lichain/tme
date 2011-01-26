#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <pthread.h>
#include <string.h>
#include <netinet/in.h>
#include <unistd.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <iostream>
#include <netinet/tcp.h>
#include <openssl/md5.h>
#include "MistMessage.pb.h"
#include "GateTalk.pb.h"
using namespace com::trendmicro::mist::proto;

#define MIST_PORT 9498

typedef struct{
    int isSink;
    uint32_t size;
    char exName[100];
    int count;
    int idx;
    unsigned int interval;
}param;

int* sessArray;
int error=0;
time_t start=0;

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
    if((sock=connectTo(MIST_PORT))<0)
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

int createSession(){
    int session_id=-1;

    Command cmd;
    Session* sessPtr=cmd.add_session();
    sessPtr->mutable_connection()->set_host_name("");
    sessPtr->mutable_connection()->set_host_port("");
    sessPtr->mutable_connection()->set_username("");
    sessPtr->mutable_connection()->set_password("");
    sessPtr->mutable_connection()->set_broker_type("");

    Command res;
    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            session_id=atoi(res.response(0).context().c_str());

    return session_id;
}

int mountExchange(int isSink, int sessId, const char* exName){
    int result=-1;
    Command cmd;
    Client* clientPtr=cmd.add_client();
    clientPtr->set_session_id(sessId);
    clientPtr->mutable_channel()->set_name(exName);
    clientPtr->mutable_channel()->set_type(Channel::QUEUE);
    clientPtr->set_type(isSink?Client::PRODUCER:Client::CONSUMER);
    clientPtr->set_action(Client::MOUNT);

    Command res;
    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            result=0;
    return result;
}

int destroySession(int sessId){
    int result=-1;
    Command cmd;
    Request* reqPtr=cmd.add_request();
    reqPtr->set_type(Request::SESSION_DESTROY);
    char buf[20];
    snprintf(buf,20,"%d",sessId);
    reqPtr->set_argument(buf);

    Command res;
    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            result=0;
    return result;
}

int attachSession(int isSink,int sessId){
    int sock=-1;
    Command cmd;
    Command res;
    Request* reqPtr=cmd.add_request();
    reqPtr->set_type(Request::CLIENT_ATTACH);
    char buf[20];
    snprintf(buf,20,"%d",sessId);
    reqPtr->set_argument(buf);
    reqPtr->set_role(isSink?Request::SINK:Request::SOURCE);

    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            sock=connectTo(atoi(res.response(0).context().c_str()));
    int trueflag=1;
    setsockopt(sock,IPPROTO_TCP,TCP_NODELAY,&trueflag,sizeof(int));
    return sock;
}

int detachSession(int isSink,int sessId){
    int result=-1;
    Command cmd;
    Request* reqPtr=cmd.add_request();
    reqPtr->set_type(Request::CLIENT_DETACH);
    char buf[20];
    snprintf(buf,20,"%d",sessId);
    reqPtr->set_argument(buf);
    reqPtr->set_role(isSink?Request::SINK:Request::SOURCE);

    Command res;
    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            result=0;
    return result;
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

void* consumer(void* p){
    param prm=*(param*)p;
    fprintf(stderr,"consumer %d: created\n",prm.idx);
    char* buf=NULL;

    char fnbuf[100];
    sprintf(fnbuf,"t_socket_source.out.%d",prm.idx);
    FILE* fp=fopen(fnbuf,"w");

    int sessid=createSession();
    if(sessid<0){
        error=1;
        pthread_exit(NULL);
    }
    sessArray[prm.idx]=sessid;
    fprintf(stderr,"consumer %d: session %d created\n",prm.idx,sessid);

    if(mountExchange(prm.isSink,sessid,prm.exName)!=0){
        error=1;
        pthread_exit(NULL);
    }
    fprintf(stderr,"consumer %d: session %d mounted\n",prm.idx,sessid);

    int sock=attachSession(prm.isSink,sessid);
    if(sock<0){
        error=1;
        pthread_exit(NULL);
    }
    fprintf(stderr,"consumer %d: session %d attached\n",prm.idx,sessid);
    
    Response ackResponse;
    ackResponse.set_success(true);

    unsigned char md5[MD5_DIGEST_LENGTH];
    int i=1;
    for(;;){
        MessageBlock msg;
        uint32_t besize;
        if(read(sock,&besize,4)<=0)
            pthread_exit(NULL);
        
        if(buf==NULL)
            buf=(char*)malloc(ntohl(besize)+200);
        int n=read_all(sock,buf,ntohl(besize));
        msg.ParseFromArray(buf,ntohl(besize));
                        
        MD5((unsigned char*)msg.message().c_str(),msg.message().length(),md5);
        int k;
        for(int k=0;k<MD5_DIGEST_LENGTH;k++)
            fprintf(fp,"%02x",md5[k]);
        fprintf(fp,"\n");
        fflush(fp);

        uint32_t ackSize=htonl(ackResponse.ByteSize());
        write(sock,&ackSize,4);
        ackResponse.SerializeToFileDescriptor(sock);
        i++;
    }
    close(sock);
    fclose(fp);
    pthread_exit(NULL);
    return NULL;
}


int main(int argc, char* argv[]){
    if(argc<3){
        fprintf(stderr,"usage: %s [consumer count] [exchange name]\n", argv[0]);
        exit(-1);
    }
    int cons=atoi(argv[1]);
    fprintf(stderr,"program started\n");
    sessArray=(int*)malloc(sizeof(int)*cons);

    int i;
    pthread_t* th_conAry=(pthread_t*)malloc(sizeof(pthread_t)*cons);
    for(i=0;i<cons;i++){
        param* conprm=(param*)malloc(sizeof(param));
        conprm->isSink=0;
        strcpy(conprm->exName,argv[2]);
        conprm->idx=i;
        pthread_create(&(th_conAry[i]),NULL,consumer,(void*)conprm);
    }

    for(i=0;i<cons;i++){
        pthread_join(th_conAry[i],NULL);
    }
    
    if(error)
        exit(-1);
    else
        exit(0);
}
